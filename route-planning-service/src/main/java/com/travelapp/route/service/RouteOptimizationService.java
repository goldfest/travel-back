package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        Map<Long, Integer> visitOverrides = payload.getVisitMinutesByRoutePointId() != null
                ? payload.getVisitMinutesByRoutePointId()
                : Map.of();
        Map<Long, RouteOptimizationRequest.RouteOptimizationDayRequest> daySettings = payload.getDaySettings() == null
                ? Map.of()
                : payload.getDaySettings().stream()
                .filter(Objects::nonNull)
                .filter(day -> day.getRouteDayId() != null)
                .collect(Collectors.toMap(RouteOptimizationRequest.RouteOptimizationDayRequest::getRouteDayId, day -> day, (a, b) -> b));

        LocalDate baseDate = resolveBaseDate(route, daySettings);

        for (RouteDay day : route.getRouteDays().stream().sorted(Comparator.comparing(RouteDay::getDayNumber)).toList()) {
            RouteOptimizationRequest.RouteOptimizationDayRequest dayRequest = day.getId() != null ? daySettings.get(day.getId()) : null;
            applyDayIdentity(day, baseDate, dayRequest);

            List<RoutePoint> orderedPoints = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (orderedPoints.isEmpty()) {
                applyDayWindow(day, dayRequest);
                continue;
            }

            visitOverrides.forEach((routePointId, minutes) -> {
                if (minutes == null || minutes <= 0) {
                    return;
                }
                orderedPoints.stream()
                        .filter(point -> routePointId.equals(point.getId()))
                        .findFirst()
                        .ifPresent(point -> point.setEstimatedVisitMinutes(minutes));
            });

            hydrateCoordinates(orderedPoints);
            Map<Long, PoiResponse> poiMap = loadPoiDetails(orderedPoints);

            List<RoutePoint> optimizedPoints = MODE_USER_ORDER.equals(mode)
                    ? new ArrayList<>(orderedPoints)
                    : reorderByFastestOpenPath(route, orderedPoints, day, dayRequest, poiMap);

            replaceDayPoints(day, optimizedPoints);
            scheduleDay(day, dayRequest, route.getCityId(), route.getTransportMode(), poiMap);
        }

        route.setIsOptimized(true);
        route.setOptimizationMode(mode);
        return route;
    }

    private void replaceDayPoints(RouteDay day, List<RoutePoint> optimizedPoints) {
        day.getRoutePoints().clear();
        short order = 1;
        for (RoutePoint point : optimizedPoints) {
            point.setOrderIndex(order++);
            point.setRouteDay(day);
            day.getRoutePoints().add(point);
        }
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

    private List<RoutePoint> reorderByFastestOpenPath(
            Route route,
            List<RoutePoint> points,
            RouteDay day,
            RouteOptimizationRequest.RouteOptimizationDayRequest dayRequest,
            Map<Long, PoiResponse> poiMap
    ) {
        if (points.size() <= 1) {
            return new ArrayList<>(points);
        }

        TravelMatrixResult matrix = buildMatrix(route.getCityId(), points, route.getTransportMode());
        int[] order = buildBestOrder(matrix);
        List<RoutePoint> reordered = new ArrayList<>(points.size());
        for (int index : order) {
            reordered.add(points.get(index));
        }
        return enforceOpeningHours(reordered, day, dayRequest, poiMap, route.getCityId(), route.getTransportMode());
    }

    private List<RoutePoint> enforceOpeningHours(
            List<RoutePoint> points,
            RouteDay day,
            RouteOptimizationRequest.RouteOptimizationDayRequest dayRequest,
            Map<Long, PoiResponse> poiMap,
            Long cityId,
            Route.TransportMode transportMode
    ) {
        List<RoutePoint> remaining = new ArrayList<>(points);
        List<RoutePoint> result = new ArrayList<>();
        LocalDateTime cursor = resolveDayStart(day, dayRequest);
        RoutePoint previous = null;

        while (!remaining.isEmpty()) {
            RoutePoint best = null;
            LocalDateTime bestArrival = null;
            long bestScore = Long.MAX_VALUE;

            for (RoutePoint candidate : remaining) {
                int travelMinutes = previous == null ? 0 : estimateTravelMinutes(cityId, transportMode, previous, candidate);
                LocalDateTime arrivalCandidate = cursor.plusMinutes(travelMinutes);
                LocalDateTime feasibleArrival = adjustToOpening(arrivalCandidate, candidate, day, poiMap);
                LocalDateTime departure = feasibleArrival.plusMinutes(resolveVisitMinutes(candidate));
                if (!fitsWindow(candidate, feasibleArrival, departure, day, poiMap, dayRequest)) {
                    continue;
                }
                long waitMinutes = Math.max(0, Duration.between(arrivalCandidate, feasibleArrival).toMinutes());
                long score = travelMinutes * 10L + waitMinutes;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                    bestArrival = feasibleArrival;
                }
            }

            if (best == null) {
                result.addAll(remaining);
                break;
            }

            result.add(best);
            cursor = bestArrival.plusMinutes(resolveVisitMinutes(best));
            previous = best;
            remaining.remove(best);
        }
        return result;
    }

    private void scheduleDay(
            RouteDay day,
            RouteOptimizationRequest.RouteOptimizationDayRequest request,
            Long cityId,
            Route.TransportMode transportMode,
            Map<Long, PoiResponse> poiMap
    ) {
        List<RoutePoint> points = day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .toList();
        if (points.isEmpty()) {
            applyDayWindow(day, request);
            return;
        }

        LocalDateTime dayStart = resolveDayStart(day, request);
        LocalDateTime dayEnd = resolveDayEnd(day, request);
        LocalDateTime cursor = dayStart;
        RoutePoint previous = null;

        for (RoutePoint point : points) {
            if (previous != null) {
                cursor = cursor.plusMinutes(estimateTravelMinutes(cityId, transportMode, previous, point));
            }

            LocalDateTime actualArrival = adjustToOpening(cursor, point, day, poiMap);
            LocalDateTime departure = actualArrival.plusMinutes(resolveVisitMinutes(point));
            if (!fitsWindow(point, actualArrival, departure, day, poiMap, request)) {
                LocalDateTime fallbackArrival = cursor;
                LocalDateTime fallbackDeparture = fallbackArrival.plusMinutes(resolveVisitMinutes(point));
                point.setPlannedArrivalAt(fallbackArrival);
                point.setPlannedDepartureAt(fallbackDeparture);
                cursor = fallbackDeparture;
            } else {
                point.setPlannedArrivalAt(actualArrival);
                point.setPlannedDepartureAt(departure);
                cursor = departure;
            }
            previous = point;
        }

        day.setPlannedStart(dayStart);
        day.setPlannedEnd(cursor.isAfter(dayEnd) ? cursor : dayEnd);
    }

    private LocalDateTime adjustToOpening(LocalDateTime candidate, RoutePoint point, RouteDay day, Map<Long, PoiResponse> poiMap) {
        PoiResponse poi = poiMap.get(point.getPoiId());
        TimeWindow window = resolvePoiWindow(poi, day, candidate.toLocalDate());
        if (window == null) {
            return candidate;
        }
        return candidate.isBefore(window.openAt()) ? window.openAt() : candidate;
    }

    private boolean fitsWindow(
            RoutePoint point,
            LocalDateTime arrival,
            LocalDateTime departure,
            RouteDay day,
            Map<Long, PoiResponse> poiMap,
            RouteOptimizationRequest.RouteOptimizationDayRequest request
    ) {
        LocalDateTime dayEnd = resolveDayEnd(day, request);
        if (departure.isAfter(dayEnd)) {
            return false;
        }
        PoiResponse poi = poiMap.get(point.getPoiId());
        TimeWindow window = resolvePoiWindow(poi, day, arrival.toLocalDate());
        return window == null || !arrival.isBefore(window.openAt()) && !departure.isAfter(window.closeAt());
    }

    private TimeWindow resolvePoiWindow(PoiResponse poi, RouteDay day, LocalDate dayDate) {
        if (poi == null || poi.getHours() == null || poi.getHours().isEmpty()) {
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
        day.setPlannedStart(start);
        day.setPlannedEnd(end.isAfter(start) ? end : start.plusHours(8));
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
            return poiClient.getPoisBatch(poiIds).stream().collect(Collectors.toMap(PoiResponse::getId, poi -> poi, (a, b) -> a));
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

    private TravelMatrixResult buildMatrix(Long cityId, List<RoutePoint> points, Route.TransportMode transportMode) {
        List<RoutingPoint> routingPoints = points.stream()
                .map(point -> new RoutingPoint(point.getId(), point.getPoiId(), safeLatitude(point), safeLongitude(point)))
                .toList();
        return routingProvider.buildMatrix(cityId, routingPoints, transportMode);
    }

    private List<RoutePoint> reorderByFastestOpenPath(Route route, List<RoutePoint> points) { return points; }

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

    private void hydrateCoordinates(List<RoutePoint> points) {
        List<Long> poiIds = points.stream().map(RoutePoint::getPoiId).distinct().toList();
        Map<Long, PoiResponse> poiMap = new HashMap<>();
        try {
            poiClient.getPoisBatch(poiIds).forEach(poi -> poiMap.put(poi.getId(), poi));
        } catch (Exception e) {
            log.warn("Failed to hydrate route optimization batch coordinates, fallback to single fetch", e);
            for (Long poiId : poiIds) {
                try {
                    PoiResponse poi = poiClient.getPoiById(poiId);
                    if (poi != null) poiMap.put(poiId, poi);
                } catch (Exception ignored) {
                    log.warn("Failed to load poi {} during optimization hydration", poiId);
                }
            }
        }
        points.forEach(point -> {
            PoiResponse poi = poiMap.get(point.getPoiId());
            if (poi != null) {
                point.setPoiDetails(
                        poi.getName(),
                        poi.getAddress(),
                        poi.getLatitude(),
                        poi.getLongitude(),
                        poi.getPoiType() != null ? poi.getPoiType().getName() : point.getPoiType()
                );
            }
        });
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

    private record TimeWindow(LocalDateTime openAt, LocalDateTime closeAt) {}
}
