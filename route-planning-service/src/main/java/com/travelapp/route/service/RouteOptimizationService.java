package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.exception.RouteValidationException;
import com.travelapp.route.model.dto.request.RouteOptimizationRequest;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private static final String MODE_TIME_WINDOW = "TIME_WINDOW";
    private static final String MODE_USER_ORDER = "USER_ORDER";
    private static final int DEFAULT_VISIT_MINUTES = 60;

    private final PoiClient poiClient;
    private final RoutingProvider routingProvider;

    public Route optimizeRoute(Route route, RouteOptimizationRequest request) {
        RouteOptimizationRequest payload = request != null ? request : new RouteOptimizationRequest();
        String mode = normalizeMode(payload.getOptimizationMode());
        Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> daySettings = mapDaySettings(payload);
        validateOptimizationRequest(route, payload, daySettings);

        LocalDate baseDate = resolveBaseDate(route, daySettings);
        List<RouteDay> sortedDays = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .toList();

        Map<Long, DayPlanState> dayStates = new LinkedHashMap<>();
        for (RouteDay day : sortedDays) {
            RouteOptimizationRequest.RouteOptimizationDayRequest dayRequest = day.getId() != null ? daySettings.get(day.getId()) : null;
            applyDayIdentity(day, baseDate, dayRequest);
            applyDayWindow(day, dayRequest);
            dayStates.put(day.getId(), new DayPlanState(day, dayRequest, day.getPlannedStart(), null, (short) 1));
        }

        List<RoutePoint> allPoints = sortedDays.stream()
                .flatMap(day -> day.getRoutePoints().stream().sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<RoutePoint, PlannedPointAssignment> assignments = new IdentityHashMap<>();
        allPoints.forEach(point -> assignments.put(point, PlannedPointAssignment.unscheduled(point, point.getRouteDay())));

        applyVisitOverrides(allPoints, payload.getVisitMinutesByRoutePointId());
        Map<Long, PoiResponse> poiMap = loadPoiDetails(allPoints);
        hydrateCoordinates(allPoints, poiMap);
        clearPointSchedules(allPoints);

        if (MODE_USER_ORDER.equals(mode)) {
            for (RouteDay day : sortedDays) {
                schedulePreservingOrder(dayStates.get(day.getId()), poiMap, route.getCityId(), route.getTransportMode(), assignments);
            }
        } else {
            scheduleAutomatically(sortedDays, dayStates, allPoints, poiMap, route.getCityId(), route.getTransportMode(), assignments);
        }

        applyAssignments(sortedDays, assignments);

        route.setIsOptimized(true);
        route.setOptimizationMode(mode);
        return route;
    }

    public Map<String, Object> buildOptimizationSummary(Route route) {
        List<RoutePoint> allPoints = route.getRouteDays().stream()
                .flatMap(day -> day.getRoutePoints().stream())
                .toList();

        List<Long> scheduledPointIds = allPoints.stream()
                .filter(this::isScheduled)
                .map(RoutePoint::getId)
                .filter(Objects::nonNull)
                .toList();

        List<Map<String, Object>> unscheduledPoints = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .flatMap(day -> day.getRoutePoints().stream()
                        .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                        .filter(point -> !isScheduled(point))
                        .map(point -> buildUnscheduledPointSummary(day, point)))
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", normalizeMode(route.getOptimizationMode()));
        summary.put("scheduledPointsCount", scheduledPointIds.size());
        summary.put("unscheduledPointsCount", unscheduledPoints.size());
        summary.put("scheduledPointIds", scheduledPointIds);
        summary.put("unscheduledPoints", unscheduledPoints);
        return summary;
    }

    private Map<String, Object> buildUnscheduledPointSummary(RouteDay day, RoutePoint point) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("routePointId", point.getId());
        item.put("poiId", point.getPoiId());
        item.put("poiName", point.getPoiName());
        item.put("routeDayId", day.getId());
        item.put("dayNumber", day.getDayNumber());
        item.put("routeDate", day.getRouteDate());
        item.put("reason", "Не удалось встроить объект в окно дня с учетом графика работы и времени посещения");
        return item;
    }

    private void validateOptimizationRequest(
            Route route,
            RouteOptimizationRequest payload,
            Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> daySettings
    ) {
        if (route.getRouteDays() == null || route.getRouteDays().isEmpty()) {
            throw new RouteValidationException("Маршрут не содержит дней для оптимизации");
        }

        List<RouteDay> days = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .toList();

        if (daySettings.size() != days.size()) {
            throw new RouteValidationException("Для оптимизации необходимо указать настройки для каждого дня маршрута");
        }

        Set<Long> routeDayIds = days.stream()
                .map(RouteDay::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map.Entry<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> entry : daySettings.entrySet()) {
            Long routeDayId = entry.getKey();
            RouteOptimizationRequest.RouteOptimizationDayRequest dayRequest = entry.getValue();
            if (routeDayId == null || !routeDayIds.contains(routeDayId)) {
                throw new RouteValidationException("Настройки оптимизации содержат день, который не принадлежит маршруту");
            }
            if (dayRequest.getRouteDate() == null) {
                throw new RouteValidationException("Для каждого дня маршрута должна быть указана дата");
            }
            if (dayRequest.getDayStartTime() == null || dayRequest.getDayEndTime() == null) {
                throw new RouteValidationException("Для каждого дня маршрута необходимо указать начало и окончание дня");
            }
            if (!dayRequest.getDayStartTime().isBefore(dayRequest.getDayEndTime())) {
                throw new RouteValidationException("Время начала дня должно быть раньше времени окончания");
            }
        }

        Map<Long, Integer> visitOverrides = payload.getVisitMinutesByRoutePointId();
        if (visitOverrides == null || visitOverrides.isEmpty()) {
            return;
        }

        Set<Long> routePointIds = route.getRouteDays().stream()
                .flatMap(day -> day.getRoutePoints().stream())
                .map(RoutePoint::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map.Entry<Long, Integer> entry : visitOverrides.entrySet()) {
            if (entry.getKey() == null || !routePointIds.contains(entry.getKey())) {
                throw new RouteValidationException("Переопределение длительности содержит точку, которой нет в маршруте");
            }
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new RouteValidationException("Длительность посещения должна быть больше нуля");
            }
        }
    }

    private Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> mapDaySettings(RouteOptimizationRequest payload) {
        if (payload.getDaySettings() == null) {
            return Map.of();
        }

        Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> result = new LinkedHashMap<>();
        Set<Long> duplicates = new HashSet<>();
        for (RouteOptimizationRequest.RouteOptimizationDayRequest daySetting : payload.getDaySettings()) {
            if (daySetting == null || daySetting.getRouteDayId() == null) {
                continue;
            }
            if (result.putIfAbsent(daySetting.getRouteDayId(), daySetting) != null) {
                duplicates.add(daySetting.getRouteDayId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new RouteValidationException("Настройки дней содержат дублирующиеся routeDayId");
        }
        return result;
    }

    private void applyVisitOverrides(List<RoutePoint> points, Map<Long, Integer> visitOverrides) {
        if (visitOverrides == null || visitOverrides.isEmpty()) {
            return;
        }
        Map<Long, RoutePoint> pointMap = points.stream()
                .filter(point -> point.getId() != null)
                .collect(Collectors.toMap(RoutePoint::getId, point -> point, (a, b) -> a));
        visitOverrides.forEach((routePointId, minutes) -> {
            RoutePoint point = pointMap.get(routePointId);
            if (point != null && minutes != null && minutes > 0) {
                point.setEstimatedVisitMinutes(minutes);
            }
        });
    }

    private void clearPointSchedules(List<RoutePoint> points) {
        points.forEach(point -> {
            point.setPlannedArrivalAt(null);
            point.setPlannedDepartureAt(null);
        });
    }

    private void schedulePreservingOrder(
            DayPlanState state,
            Map<Long, PoiResponse> poiMap,
            Long cityId,
            Route.TransportMode transportMode,
            Map<RoutePoint, PlannedPointAssignment> assignments
    ) {
        RouteDay day = state.day();
        List<RoutePoint> orderedPoints = day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .collect(Collectors.toCollection(ArrayList::new));

        for (RoutePoint point : orderedPoints) {
            SlotResult slot = trySchedulePoint(state, point, poiMap, cityId, transportMode);
            if (slot.feasible()) {
                assignments.put(point, assignPointToDay(state, point, slot));
            } else {
                markUnscheduled(point);
                assignments.put(point, PlannedPointAssignment.unscheduled(point, day));
            }
        }
    }

    private void scheduleAutomatically(
            List<RouteDay> sortedDays,
            Map<Long, DayPlanState> dayStates,
            List<RoutePoint> allPoints,
            Map<Long, PoiResponse> poiMap,
            Long cityId,
            Route.TransportMode transportMode,
            Map<RoutePoint, PlannedPointAssignment> assignments
    ) {
        Map<Long, Long> originalDayIdByPointId = allPoints.stream()
                .filter(point -> point.getId() != null && point.getRouteDay() != null && point.getRouteDay().getId() != null)
                .collect(Collectors.toMap(RoutePoint::getId, point -> point.getRouteDay().getId(), (a, b) -> a));

        List<RoutePoint> remaining = new ArrayList<>(allPoints);
        boolean progress = true;
        while (progress && !remaining.isEmpty()) {
            progress = false;
            for (RouteDay day : sortedDays) {
                DayPlanState state = dayStates.get(day.getId());
                CandidateChoice choice = selectBestCandidate(state, remaining, poiMap, cityId, transportMode);
                if (choice == null) {
                    continue;
                }
                assignments.put(choice.point(), assignPointToDay(state, choice.point(), choice.slot()));
                remaining.remove(choice.point());
                progress = true;
            }
        }

        for (RoutePoint point : remaining) {
            markUnscheduled(point);
            Long originalDayId = point.getId() != null ? originalDayIdByPointId.get(point.getId()) : null;
            if (originalDayId == null && point.getRouteDay() != null) {
                originalDayId = point.getRouteDay().getId();
            }
            RouteDay targetDay = null;
            if (originalDayId != null) {
                final Long dayId = originalDayId;
                targetDay = sortedDays.stream()
                        .filter(day -> Objects.equals(day.getId(), dayId))
                        .findFirst()
                        .orElse(null);
            }
            if (targetDay == null && !sortedDays.isEmpty()) {
                targetDay = sortedDays.get(sortedDays.size() - 1);
            }
            if (targetDay != null) {
                assignments.put(point, PlannedPointAssignment.unscheduled(point, targetDay));
            }
        }
    }

    private CandidateChoice selectBestCandidate(
            DayPlanState state,
            List<RoutePoint> remaining,
            Map<Long, PoiResponse> poiMap,
            Long cityId,
            Route.TransportMode transportMode
    ) {
        CandidateChoice best = null;
        long bestScore = Long.MAX_VALUE;

        for (RoutePoint candidate : remaining) {
            SlotResult slot = trySchedulePoint(state, candidate, poiMap, cityId, transportMode);
            if (!slot.feasible()) {
                continue;
            }
            long score = slot.travelMinutes() * 10L + slot.waitMinutes() * 3L
                    + Duration.between(state.day().getPlannedStart(), slot.departure()).toMinutes();
            if (score < bestScore) {
                bestScore = score;
                best = new CandidateChoice(candidate, slot);
            }
        }

        return best;
    }

    private SlotResult trySchedulePoint(
            DayPlanState state,
            RoutePoint point,
            Map<Long, PoiResponse> poiMap,
            Long cityId,
            Route.TransportMode transportMode
    ) {
        int travelMinutes = state.previousPoint() == null
                ? 0
                : estimateTravelMinutes(cityId, transportMode, state.previousPoint(), point);
        LocalDateTime rawArrival = state.cursor().plusMinutes(travelMinutes);
        PoiResponse poi = poiMap.get(point.getPoiId());
        TimeWindow window = resolvePoiWindow(poi, state.day(), state.day().getRouteDate());
        if (window != null && rawArrival.isAfter(window.closeAt())) {
            return SlotResult.infeasible("Прибытие после закрытия", travelMinutes, 0L);
        }

        LocalDateTime visitStart = window != null && rawArrival.isBefore(window.openAt())
                ? window.openAt()
                : rawArrival;
        LocalDateTime departure = visitStart.plusMinutes(resolveVisitMinutes(point));

        if (window != null && departure.isAfter(window.closeAt())) {
            return SlotResult.infeasible("Посещение не помещается в часы работы объекта", travelMinutes,
                    Math.max(0, Duration.between(rawArrival, visitStart).toMinutes()));
        }
        if (departure.isAfter(state.day().getPlannedEnd())) {
            return SlotResult.infeasible("Посещение не помещается в окно дня", travelMinutes,
                    Math.max(0, Duration.between(rawArrival, visitStart).toMinutes()));
        }

        return SlotResult.feasible(rawArrival, visitStart, departure, travelMinutes,
                Math.max(0, Duration.between(rawArrival, visitStart).toMinutes()));
    }

    private PlannedPointAssignment assignPointToDay(DayPlanState state, RoutePoint point, SlotResult slot) {
        RouteDay day = state.day();
        PlannedPointAssignment assignment = PlannedPointAssignment.scheduled(
                point,
                day,
                state.nextOrderIndex(),
                slot.visitStart(),
                slot.departure()
        );
        state.setCursor(slot.departure());
        state.setPreviousPoint(point);
        state.incrementOrder();
        return assignment;
    }

    private void markUnscheduled(RoutePoint point) {
        point.setPlannedArrivalAt(null);
        point.setPlannedDepartureAt(null);
    }

    private void applyAssignments(
            List<RouteDay> sortedDays,
            Map<RoutePoint, PlannedPointAssignment> assignments
    ) {
        Map<Long, List<PlannedPointAssignment>> byDayId = new LinkedHashMap<>();

        for (PlannedPointAssignment assignment : assignments.values()) {
            RouteDay targetDay = assignment.targetDay();
            if (targetDay == null || targetDay.getId() == null) {
                continue;
            }
            byDayId.computeIfAbsent(targetDay.getId(), ignored -> new ArrayList<>()).add(assignment);
        }

        for (RouteDay day : sortedDays) {
            List<PlannedPointAssignment> planned = byDayId.getOrDefault(day.getId(), List.of()).stream()
                    .sorted(Comparator
                            .comparing((PlannedPointAssignment a) -> a.plannedArrivalAt() == null)
                            .thenComparing(PlannedPointAssignment::plannedArrivalAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(a -> a.point().getOrderIndex(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toCollection(ArrayList::new));

            short nextOrder = 1;
            for (PlannedPointAssignment assignment : planned) {
                RoutePoint point = assignment.point();
                point.setRouteDay(day);
                point.setOrderIndex(nextOrder++);
                point.setPlannedArrivalAt(assignment.plannedArrivalAt());
                point.setPlannedDepartureAt(assignment.plannedDepartureAt());
            }

            day.getRoutePoints().removeIf(point -> point.getRouteDay() != day);
            for (PlannedPointAssignment assignment : planned) {
                RoutePoint point = assignment.point();
                if (!day.getRoutePoints().contains(point)) {
                    day.getRoutePoints().add(point);
                }
            }

            day.getRoutePoints().sort(Comparator.comparing(RoutePoint::getOrderIndex));
        }
    }

    private boolean isScheduled(RoutePoint point) {
        return point.getPlannedArrivalAt() != null && point.getPlannedDepartureAt() != null;
    }

    private LocalDate resolveBaseDate(Route route, Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> daySettings) {
        Optional<LocalDate> explicit = daySettings.values().stream()
                .map(RouteOptimizationRequest.RouteOptimizationDayRequest::getRouteDate)
                .filter(Objects::nonNull)
                .findFirst();
        if (explicit.isPresent()) {
            return explicit.get();
        }
        return route.getRouteDays().stream()
                .map(RouteDay::getRouteDate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> route.getRouteDays().stream()
                        .map(RouteDay::getPlannedStart)
                        .filter(Objects::nonNull)
                        .map(LocalDateTime::toLocalDate)
                        .findFirst()
                        .orElse(LocalDate.now()));
    }

    private void applyDayIdentity(RouteDay day, LocalDate baseDate, RouteOptimizationRequest.RouteOptimizationDayRequest request) {
        LocalDate dayDate = request != null && request.getRouteDate() != null
                ? request.getRouteDate()
                : baseDate.plusDays(Math.max(0, day.getDayNumber() - 1L));
        day.setRouteDate(dayDate);
    }

    private TimeWindow resolvePoiWindow(PoiResponse poi, RouteDay day, LocalDate dayDate) {
        if (poi == null || poi.getHours() == null || poi.getHours().isEmpty() || dayDate == null) {
            return null;
        }
        DayOfWeek dayOfWeek = dayDate.getDayOfWeek();
        return poi.getHours().stream()
                .filter(Objects::nonNull)
                .filter(hours -> hours.getDayOfWeek() != null && hours.getDayOfWeek() == dayOfWeek.getValue())
                .findFirst()
                .map(hours -> {
                    if (Boolean.TRUE.equals(hours.getClosed())) {
                        return null;
                    }
                    LocalTime open = Boolean.TRUE.equals(hours.getAroundTheClock()) || hours.getOpenTime() == null
                            ? LocalTime.MIN
                            : hours.getOpenTime();
                    LocalTime close = Boolean.TRUE.equals(hours.getAroundTheClock()) || hours.getCloseTime() == null
                            ? LocalTime.MAX.minusNanos(1)
                            : hours.getCloseTime();
                    if (!close.isAfter(open)) {
                        close = LocalTime.MAX.minusNanos(1);
                    }
                    return new TimeWindow(dayDate.atTime(open), dayDate.atTime(close));
                })
                .orElse(null);
    }

    private int estimateTravelMinutes(Long cityId, Route.TransportMode transportMode, RoutePoint from, RoutePoint to) {
        TravelMatrixResult matrix = buildMatrix(cityId, List.of(from, to), transportMode);
        if (matrix.getDurationMin().length < 2 || matrix.getDurationMin()[0].length < 2) {
            return 0;
        }
        return Math.max(0, matrix.getDurationMin()[0][1]);
    }

    private int resolveVisitMinutes(RoutePoint point) {
        return point.getEstimatedVisitMinutes() != null && point.getEstimatedVisitMinutes() > 0
                ? point.getEstimatedVisitMinutes()
                : DEFAULT_VISIT_MINUTES;
    }

    private void applyDayWindow(RouteDay day, RouteOptimizationRequest.RouteOptimizationDayRequest request) {
        LocalDateTime start = resolveDayStart(day, request);
        LocalDateTime end = resolveDayEnd(day, request);
        if (!end.isAfter(start)) {
            throw new RouteValidationException("Окно дня задано некорректно");
        }
        day.setPlannedStart(start);
        day.setPlannedEnd(end);
    }

    private LocalDateTime resolveDayStart(RouteDay day, RouteOptimizationRequest.RouteOptimizationDayRequest request) {
        LocalDate date = day.getRouteDate() != null ? day.getRouteDate()
                : day.getPlannedStart() != null ? day.getPlannedStart().toLocalDate()
                : LocalDate.now();
        LocalTime time = request != null && request.getDayStartTime() != null
                ? request.getDayStartTime()
                : day.getPlannedStart() != null ? day.getPlannedStart().toLocalTime() : LocalTime.of(9, 0);
        return date.atTime(time);
    }

    private LocalDateTime resolveDayEnd(RouteDay day, RouteOptimizationRequest.RouteOptimizationDayRequest request) {
        LocalDate date = day.getRouteDate() != null ? day.getRouteDate()
                : day.getPlannedEnd() != null ? day.getPlannedEnd().toLocalDate()
                : day.getPlannedStart() != null ? day.getPlannedStart().toLocalDate() : LocalDate.now();
        LocalTime time = request != null && request.getDayEndTime() != null
                ? request.getDayEndTime()
                : day.getPlannedEnd() != null ? day.getPlannedEnd().toLocalTime() : LocalTime.of(18, 0);
        return date.atTime(time);
    }

    private Map<Long, PoiResponse> loadPoiDetails(List<RoutePoint> points) {
        List<Long> poiIds = points.stream().map(RoutePoint::getPoiId).distinct().toList();
        try {
            return poiClient.getPoisBatch(poiIds).stream()
                    .collect(Collectors.toMap(PoiResponse::getId, poi -> poi, (a, b) -> a));
        } catch (Exception ex) {
            log.warn("Failed to load poi batch for optimization, fallback to single loads", ex);
            Map<Long, PoiResponse> result = new HashMap<>();
            for (Long poiId : poiIds) {
                try {
                    result.put(poiId, poiClient.getPoiById(poiId));
                } catch (Exception ignored) {
                    log.warn("Failed to load poi {}", poiId);
                }
            }
            return result;
        }
    }

    private void hydrateCoordinates(List<RoutePoint> points, Map<Long, PoiResponse> poiMap) {
        points.forEach(point -> {
            PoiResponse poi = poiMap.get(point.getPoiId());
            if (poi != null) {
                point.setPoiDetails(
                        poi.getName(),
                        poi.getAddress(),
                        poi.getLatitude(),
                        poi.getLongitude(),
                        poi.getPoiType() != null ? poi.getPoiType().getCode() : point.getPoiType()
                );
            }
        });
    }

    private TravelMatrixResult buildMatrix(Long cityId, List<RoutePoint> points, Route.TransportMode transportMode) {
        List<RoutingPoint> routingPoints = points.stream()
                .map(point -> new RoutingPoint(point.getId(), point.getPoiId(), safeLatitude(point), safeLongitude(point)))
                .toList();
        return routingProvider.buildMatrix(cityId, routingPoints, transportMode);
    }

    private int[] buildBestOrder(TravelMatrixResult matrix) {
        int n = matrix.getDurationMin().length;
        if (n <= 1) return buildIdentity(n);
        if (n <= 10) return exactHeldKarpOpenPath(matrix);
        return twoOpt(nearestNeighborOrder(matrix), matrix);
    }

    private int[] exactHeldKarpOpenPath(TravelMatrixResult matrix) {
        int n = matrix.getDurationMin().length;
        int size = 1 << n;
        double[][] dp = new double[size][n];
        int[][] parent = new int[size][n];
        for (double[] row : dp) Arrays.fill(row, Double.POSITIVE_INFINITY);
        for (int[] row : parent) Arrays.fill(row, -1);
        for (int i = 0; i < n; i++) dp[1 << i][i] = 0.0;
        for (int mask = 1; mask < size; mask++) {
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0 || Double.isInfinite(dp[mask][last])) continue;
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    int nextMask = mask | (1 << next);
                    double candidate = dp[mask][last] + matrix.getDurationMin()[last][next];
                    if (candidate < dp[nextMask][next]) {
                        dp[nextMask][next] = candidate;
                        parent[nextMask][next] = last;
                    }
                }
            }
        }
        int fullMask = size - 1;
        int bestLast = 0;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int last = 0; last < n; last++) {
            if (dp[fullMask][last] < bestCost) {
                bestCost = dp[fullMask][last];
                bestLast = last;
            }
        }
        int[] order = new int[n];
        int mask = fullMask;
        int current = bestLast;
        for (int i = n - 1; i >= 0; i--) {
            order[i] = current;
            int previous = parent[mask][current];
            mask ^= 1 << current;
            current = previous;
        }
        return order;
    }

    private int[] nearestNeighborOrder(TravelMatrixResult matrix) {
        int n = matrix.getDurationMin().length;
        boolean[] used = new boolean[n];
        int[] order = new int[n];
        order[0] = 0;
        used[0] = true;
        for (int pos = 1; pos < n; pos++) {
            int prev = order[pos - 1];
            int best = -1;
            int bestCost = Integer.MAX_VALUE;
            for (int next = 0; next < n; next++) {
                if (used[next]) continue;
                int cost = matrix.getDurationMin()[prev][next];
                if (cost < bestCost) {
                    bestCost = cost;
                    best = next;
                }
            }
            order[pos] = best;
            used[best] = true;
        }
        return order;
    }

    private int[] twoOpt(int[] order, TravelMatrixResult matrix) {
        int[] best = Arrays.copyOf(order, order.length);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < best.length - 2; i++) {
                for (int k = i + 1; k < best.length - 1; k++) {
                    int delta = gain(best, i, k, matrix);
                    if (delta < 0) {
                        reverse(best, i, k);
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private int gain(int[] order, int i, int k, TravelMatrixResult matrix) {
        int a = order[i - 1];
        int b = order[i];
        int c = order[k];
        int d = order[k + 1];
        return matrix.getDurationMin()[a][c] + matrix.getDurationMin()[b][d]
                - matrix.getDurationMin()[a][b] - matrix.getDurationMin()[c][d];
    }

    private void reverse(int[] order, int i, int k) {
        while (i < k) {
            int tmp = order[i];
            order[i] = order[k];
            order[k] = tmp;
            i++;
            k--;
        }
    }

    private int[] buildIdentity(int n) {
        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        return order;
    }

    private double safeLatitude(RoutePoint point) {
        return point.getPoiLatitude() != null ? point.getPoiLatitude() : 0d;
    }

    private double safeLongitude(RoutePoint point) {
        return point.getPoiLongitude() != null ? point.getPoiLongitude() : 0d;
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? MODE_TIME_WINDOW : mode.trim().toUpperCase();
        return MODE_USER_ORDER.equals(normalized) ? MODE_USER_ORDER : MODE_TIME_WINDOW;
    }

    private record CandidateChoice(RoutePoint point, SlotResult slot) {}

    private record SlotResult(
            boolean feasible,
            String failureReason,
            LocalDateTime rawArrival,
            LocalDateTime visitStart,
            LocalDateTime departure,
            int travelMinutes,
            long waitMinutes
    ) {
        static SlotResult feasible(LocalDateTime rawArrival, LocalDateTime visitStart, LocalDateTime departure, int travelMinutes, long waitMinutes) {
            return new SlotResult(true, null, rawArrival, visitStart, departure, travelMinutes, waitMinutes);
        }

        static SlotResult infeasible(String failureReason, int travelMinutes, long waitMinutes) {
            return new SlotResult(false, failureReason, null, null, null, travelMinutes, waitMinutes);
        }
    }

    private static final class DayPlanState {
        private final RouteDay day;
        private final RouteOptimizationRequest.RouteOptimizationDayRequest request;
        private LocalDateTime cursor;
        private RoutePoint previousPoint;
        private short nextOrderIndex;

        private DayPlanState(
                RouteDay day,
                RouteOptimizationRequest.RouteOptimizationDayRequest request,
                LocalDateTime cursor,
                RoutePoint previousPoint,
                short nextOrderIndex
        ) {
            this.day = day;
            this.request = request;
            this.cursor = cursor;
            this.previousPoint = previousPoint;
            this.nextOrderIndex = nextOrderIndex;
        }

        public RouteDay day() {
            return day;
        }

        public RouteOptimizationRequest.RouteOptimizationDayRequest request() {
            return request;
        }

        public LocalDateTime cursor() {
            return cursor;
        }

        public void setCursor(LocalDateTime cursor) {
            this.cursor = cursor;
        }

        public RoutePoint previousPoint() {
            return previousPoint;
        }

        public void setPreviousPoint(RoutePoint previousPoint) {
            this.previousPoint = previousPoint;
        }

        public short nextOrderIndex() {
            return nextOrderIndex;
        }

        public void incrementOrder() {
            this.nextOrderIndex++;
        }
    }

    private record TimeWindow(LocalDateTime openAt, LocalDateTime closeAt) {}

    private record PlannedPointAssignment(
            RoutePoint point,
            RouteDay targetDay,
            Short orderIndex,
            LocalDateTime plannedArrivalAt,
            LocalDateTime plannedDepartureAt
    ) {
        private static PlannedPointAssignment scheduled(
                RoutePoint point,
                RouteDay targetDay,
                Short orderIndex,
                LocalDateTime plannedArrivalAt,
                LocalDateTime plannedDepartureAt
        ) {
            return new PlannedPointAssignment(point, targetDay, orderIndex, plannedArrivalAt, plannedDepartureAt);
        }

        private static PlannedPointAssignment unscheduled(RoutePoint point, RouteDay targetDay) {
            return new PlannedPointAssignment(point, targetDay, null, null, null);
        }
    }
}
