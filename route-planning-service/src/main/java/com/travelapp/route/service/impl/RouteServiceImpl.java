package com.travelapp.route.service.impl;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.exception.RouteValidationException;
import com.travelapp.route.mapper.RouteMapper;
import com.travelapp.route.model.dto.request.*;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteDayRepository;
import com.travelapp.route.repository.RoutePointRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.service.RouteOptimizationService;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.RoutePathCacheService;
import com.travelapp.route.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final RouteDayRepository routeDayRepository;
    private final RoutePointRepository routePointRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RouteMapper routeMapper;
    private final PoiClient poiClient;
    private final RouteOptimizationService optimizationService;
    private final RoutePathCacheService routePathCacheService;
    private final GraphVersionService graphVersionService;
    private final RouteGraphPreparationCoordinator routeGraphPreparationCoordinator;

    @Override
    @Transactional
    public RouteResponse createRoute(Long userId, RouteCreateRequest request) {
        log.info("Creating route for user {}: {}", userId, request.getName());

        validateRouteName(userId, request.getName(), null);
        validateCreateRequest(request);

        Map<Long, PoiResponse> poiMap = fetchAndValidatePois(request.getCityId(), extractPoiIds(request));

        Route route = routeMapper.toEntity(request);
        route.setUserId(userId);
        route.setStatus(request.getStatus() != null ? request.getStatus() : Route.RouteStatus.DRAFT);

        route.getRouteDays().clear();
        populateRouteDays(route, request.getDays(), poiMap);

        if (Boolean.TRUE.equals(request.getAutoOptimize())) {
            route.setIsOptimized(true);
            route.setOptimizationMode(request.getOptimizationMode());
            
        RouteOptimizationRequest optimizationRequest = new RouteOptimizationRequest();
        optimizationRequest.setOptimizationMode(request.getOptimizationMode());
        route = optimizationService.optimizeRoute(route, optimizationRequest);
        }

        Route savedRoute = routeRepository.save(route);

        if (!hasAnyPoints(savedRoute)) {
            RouteResponse response = toResponseWithWarnings(savedRoute, buildWarnings(savedRoute, poiMap));
            log.info("Route created successfully without points cache rebuild: {}", savedRoute.getId());
            return response;
        }

        if (!graphVersionService.hasActiveVersion(savedRoute.getCityId())) {
            savedRoute.setStatus(Route.RouteStatus.GRAPH_PREPARING);
            savedRoute = routeRepository.save(savedRoute);
            routeGraphPreparationCoordinator.scheduleAfterCommit(savedRoute.getId(), savedRoute.getCityId());

            RouteResponse response = toResponseWithWarnings(savedRoute, buildWarnings(savedRoute, poiMap));
            response.addAdditionalProperty("buildMessage", "Маршрут строится, подождите");
            log.info("Route {} saved in GRAPH_PREPARING for city {}", savedRoute.getId(), savedRoute.getCityId());
            return response;
        }

        routePathCacheService.rebuildRoutePaths(savedRoute.getId());
        savedRoute = routeRepository.findById(savedRoute.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        recalculateRouteMetrics(savedRoute);
        savedRoute = routeRepository.save(savedRoute);

        RouteResponse response = toResponseWithWarnings(savedRoute, buildWarnings(savedRoute, poiMap));
        log.info("Route created successfully: {}", savedRoute.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse getRouteById(Long userId, Long routeId) {
        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));
        return toResponseWithWarnings(route, buildWarnings(route, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> getUserRoutes(Long userId, Pageable pageable) {
        return routeRepository.findByUserIdAndStatusNotOrderByUpdatedAtDesc(
                        userId,
                        Route.RouteStatus.ARCHIVED,
                        pageable
                )
                .map(route -> toResponseWithWarnings(route, buildWarnings(route, null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> getArchivedRoutes(Long userId, Pageable pageable) {
        return routeRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
                        userId,
                        Route.RouteStatus.ARCHIVED,
                        pageable
                )
                .map(route -> toResponseWithWarnings(route, buildWarnings(route, null)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponse> getRoutesByCity(Long userId, Long cityId) {
        return routeRepository.findByUserIdAndCityIdAndStatusNotOrderByUpdatedAtDesc(
                        userId,
                        cityId,
                        Route.RouteStatus.ARCHIVED
                ).stream()
                .map(route -> toResponseWithWarnings(route, buildWarnings(route, null)))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse updateRoute(Long userId, Long routeId, RouteUpdateRequest request) {
        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        if (request.getName() != null && !request.getName().equals(route.getName())) {
            validateRouteName(userId, request.getName(), route.getId());
        }

        boolean transportChanged = request.getTransportMode() != null
                && !request.getTransportMode().name().equals(route.getTransportMode().name());

        routeMapper.updateEntity(route, request);

        if (request.getStatus() != null) {
            route.setStatus(request.getStatus());
        }

        Route saved = routeRepository.save(route);

        if (transportChanged || hasAnyPoints(saved)) {
            routePathCacheService.rebuildRoutePaths(saved.getId());
            saved = routeRepository.findById(saved.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));
        }

        recalculateRouteMetrics(saved);
        saved = routeRepository.save(saved);

        return toResponseWithWarnings(saved, buildWarnings(saved, null));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void archiveRoute(Long userId, Long routeId) {
        Route route = getOwnedRoute(userId, routeId);
        route.setStatus(Route.RouteStatus.ARCHIVED);
        routeRepository.save(route);
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void unarchiveRoute(Long userId, Long routeId) {
        Route route = getOwnedRoute(userId, routeId);
        route.setStatus(Route.RouteStatus.READY);
        routeRepository.save(route);
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void deleteRoute(Long userId, Long routeId) {
        routeRepository.delete(getOwnedRoute(userId, routeId));
    }

    @Override
    @Transactional
    public RouteResponse duplicateRoute(Long userId, Long routeId, String newName) {
        Route original = getOwnedRoute(userId, routeId);
        String duplicateName = (newName == null || newName.isBlank()) ? original.getName() + " (копия)" : newName;
        validateRouteName(userId, duplicateName, null);

        Route duplicate = new Route();
        duplicate.setName(duplicateName);
        duplicate.setDescription(original.getDescription());
        duplicate.setCoverPhotoUrl(original.getCoverPhotoUrl());
        duplicate.setTransportMode(original.getTransportMode());
        duplicate.setUserId(userId);
        duplicate.setCityId(original.getCityId());
        duplicate.setStartPoint(original.getStartPoint());
        duplicate.setEndPoint(original.getEndPoint());
        duplicate.setStatus(Route.RouteStatus.DRAFT);

        for (RouteDay sourceDay : original.getRouteDays()) {
            RouteDay targetDay = new RouteDay();
            targetDay.setDayNumber(sourceDay.getDayNumber());
            targetDay.setDescription(sourceDay.getDescription());
            targetDay.setPlannedStart(sourceDay.getPlannedStart());
            targetDay.setPlannedEnd(sourceDay.getPlannedEnd());
            duplicate.addRouteDay(targetDay);

            for (RoutePoint sourcePoint : sourceDay.getRoutePoints()) {
                RoutePoint targetPoint = copyPoint(sourcePoint);
                targetDay.addRoutePoint(targetPoint);
            }
        }

        Route saved = routeRepository.save(duplicate);

        routePathCacheService.rebuildRoutePaths(saved.getId());
        saved = routeRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        recalculateRouteMetrics(saved);
        saved = routeRepository.save(saved);

        return toResponseWithWarnings(saved, buildWarnings(saved, null));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse addPoiToRoute(Long userId, Long routeId, Long poiId, Short dayNumber, Short orderIndex) {
        Route route = getOwnedRoute(userId, routeId);

        PoiResponse poi;
        try {
            poi = poiClient.getPoiById(poiId);
        } catch (Exception e) {
            throw new ResourceNotFoundException("Объект не найден");
        }

        if (poi == null) {
            throw new ResourceNotFoundException("Объект не найден");
        }

        validatePoiBelongsToCity(route.getCityId(), poi);

        RouteDay routeDay = resolveRouteDay(route, dayNumber);
        if (routePointRepository.existsByRouteDayIdAndPoiId(routeDay.getId(), poiId)) {
            throw new RouteValidationException("Объект уже добавлен в этот день маршрута");
        }

        short actualOrder = orderIndex != null ? orderIndex : nextOrderIndex(routeDay);
        shiftRoutePointsOrder(routeDay, actualOrder);

        RoutePoint routePoint = new RoutePoint();
        routePoint.setOrderIndex(actualOrder);
        routePoint.setPoiId(poiId);
        routePoint.setEstimatedVisitMinutes(60);
        applyPoiSnapshot(routePoint, poi);
        routeDay.addRoutePoint(routePoint);

        Route saved = routeRepository.save(route);

        routePathCacheService.rebuildRoutePaths(saved.getId());
        saved = routeRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        recalculateRouteMetrics(saved);
        saved = routeRepository.save(saved);

        return toResponseWithWarnings(saved, buildWarnings(saved, Map.of(poiId, poi)));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse removePointFromRoute(Long userId, Long routeId, Long routePointId) {
        Route route = getOwnedRoute(userId, routeId);

        RoutePoint point = routePointRepository.findByIdAndRouteDayRouteId(routePointId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Точка маршрута не найдена"));

        RouteDay day = point.getRouteDay();
        day.removeRoutePoint(point);
        routePointRepository.delete(point);

        normalizeDayOrder(day);

        Route saved = routeRepository.save(route);

        routePathCacheService.rebuildRoutePaths(saved.getId());
        saved = routeRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        recalculateRouteMetrics(saved);
        saved = routeRepository.save(saved);

        return toResponseWithWarnings(saved, buildWarnings(saved, null));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse reorderRouteDayPoints(Long userId, Long routeId, Long dayId, List<Long> pointIdsInOrder) {
        Route route = getOwnedRoute(userId, routeId);

        RouteDay day = routeDayRepository.findByIdAndRouteId(dayId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("День маршрута не найден"));

        List<RoutePoint> points = routePointRepository.findByRouteDayIdOrderByOrderIndexAsc(dayId);
        Set<Long> existingIds = points.stream().map(RoutePoint::getId).collect(Collectors.toSet());

        if (pointIdsInOrder.size() != points.size() || !existingIds.equals(new HashSet<>(pointIdsInOrder))) {
            throw new RouteValidationException("Некорректный список точек для сортировки внутри дня");
        }

        Map<Long, RoutePoint> pointMap = points.stream()
                .collect(Collectors.toMap(RoutePoint::getId, Function.identity()));

        for (int i = 0; i < pointIdsInOrder.size(); i++) {
            pointMap.get(pointIdsInOrder.get(i)).setOrderIndex((short) (i + 1));
        }

        day.getRoutePoints().sort(Comparator.comparingInt(RoutePoint::getOrderIndex));
        routePointRepository.saveAll(points);

        Route saved = routeRepository.save(route);

        routePathCacheService.rebuildRoutePaths(saved.getId());
        saved = routeRepository.findById(saved.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        recalculateRouteMetrics(saved);
        saved = routeRepository.save(saved);

        return toResponseWithWarnings(saved, buildWarnings(saved, null));
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse optimizeRoute(Long userId, Long routeId, RouteOptimizationRequest request) {
        Route route = getOwnedRoute(userId, routeId);
        RouteOptimizationRequest payload = request != null ? request : new RouteOptimizationRequest();
        String mode = payload.getOptimizationMode() != null ? payload.getOptimizationMode() : "TIME_WINDOW";
        route.setOptimizationMode(mode);
        route.setIsOptimized(true);

        optimizationService.optimizeRoute(route, payload);
        routeRepository.saveAndFlush(route);

        if (hasAnyPoints(route) && graphVersionService.hasActiveVersion(route.getCityId())) {
            routePathCacheService.rebuildRoutePaths(route.getId());
        }

        Route reloaded = routeRepository.findFullByIdAndUserId(route.getId(), userId)
                .orElse(route);
        recalculateRouteMetrics(reloaded);
        routeRepository.saveAndFlush(reloaded);

        return toResponseWithWarnings(reloaded, buildWarnings(reloaded, null));
    }

    private Route getOwnedRoute(Long userId, Long routeId) {
        return routeRepository.findFullByIdAndUserId(routeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));
    }


    @Override
    @Transactional
    public RouteResponse generateRoute(Long userId, RouteGenerateRequest request) {
        List<PoiResponse> candidates = new ArrayList<>();
        List<String> interests = request.getInterests() == null || request.getInterests().isEmpty()
                ? List.of((String) null)
                : request.getInterests();

        for (String interest : interests) {
            try {
                candidates.addAll(poiClient.searchByCityAndType(request.getCityId(), interest, 100));
            } catch (Exception e) {
                log.warn("Failed to fetch POI list for interest {}", interest, e);
            }
        }

        if (candidates.isEmpty()) {
            throw new RouteValidationException("Не удалось подобрать объекты для генерации маршрута");
        }

        List<PoiResponse> filtered = candidates.stream()
                .filter(p -> p.getCityId() != null && p.getCityId().equals(request.getCityId()))
                .filter(p -> p.getIsClosed() == null || !p.getIsClosed())
                .filter(p -> request.getBudgetLevel() == null || p.getPriceLevel() == null || p.getPriceLevel() <= request.getBudgetLevel())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PoiResponse::getId, Function.identity(), (a, b) -> a),
                        m -> new ArrayList<>(m.values())
                ));

        if (filtered.isEmpty()) {
            throw new RouteValidationException("После фильтрации не осталось подходящих объектов");
        }

        filtered.sort(Comparator.comparing((PoiResponse p) -> Boolean.TRUE.equals(p.getIsVerified()) ? 0 : 1)
                .thenComparing(PoiResponse::getName, Comparator.nullsLast(String::compareToIgnoreCase)));

        int daysCount = Optional.ofNullable(request.getDaysCount()).orElse(1);
        int pointsPerDay = Math.max(1, Math.min(5, (int) Math.ceil((double) filtered.size() / daysCount)));

        List<RouteDayCreateRequest> days = new ArrayList<>();
        int cursor = 0;

        for (int dayNumber = 1; dayNumber <= daysCount && cursor < filtered.size(); dayNumber++) {
            RouteDayCreateRequest day = new RouteDayCreateRequest();
            day.setDayNumber((short) dayNumber);
            day.setDescription("Сгенерированный день " + dayNumber);

            List<RoutePointCreateRequest> points = new ArrayList<>();
            for (int i = 0; i < pointsPerDay && cursor < filtered.size(); i++, cursor++) {
                PoiResponse poi = filtered.get(cursor);
                RoutePointCreateRequest point = new RoutePointCreateRequest();
                point.setPoiId(poi.getId());
                point.setOrderIndex((short) (i + 1));
                point.setEstimatedVisitMinutes(60);
                points.add(point);
            }

            day.setPoints(points);
            days.add(day);
        }

        RouteCreateRequest createRequest = new RouteCreateRequest();
        createRequest.setName("Сгенерированный маршрут");
        createRequest.setDescription("Маршрут, сгенерированный по интересам пользователя");
        createRequest.setCityId(request.getCityId());
        createRequest.setTransportMode(request.getTransportMode());
        createRequest.setStatus(Route.RouteStatus.DRAFT);
        createRequest.setDays(days);

        RouteResponse response = createRoute(userId, createRequest);

        if (Boolean.TRUE.equals(request.getOptimize())) {
            RouteOptimizationRequest optimizationRequest = new RouteOptimizationRequest();
            optimizationRequest.setOptimizationMode("TIME_WINDOW");
            return optimizeRoute(userId, response.getId(), optimizationRequest);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUserRoutes(Long userId) {
        return routeRepository.countByUserIdAndStatusNot(userId, Route.RouteStatus.ARCHIVED);
    }

    @Override
    public boolean isRouteNameAvailable(Long userId, String name) {
        return !routeRepository.existsByUserIdAndNameAndStatusNot(userId, name, Route.RouteStatus.ARCHIVED);
    }

    private boolean hasAnyPoints(Route route) {
        return route.getRouteDays().stream().anyMatch(day -> day.getRoutePoints() != null && !day.getRoutePoints().isEmpty());
    }

    private void validateRouteName(Long userId, String name, Long currentRouteId) {
        boolean exists = routeRepository.existsByUserIdAndNameAndStatusNot(userId, name, Route.RouteStatus.ARCHIVED);
        if (exists) {
            if (currentRouteId == null) {
                throw new RouteValidationException("Маршрут с таким названием уже существует");
            }

            Route existing = routeRepository
                    .findByUserIdAndStatusNotOrderByUpdatedAtDesc(
                            userId,
                            Route.RouteStatus.ARCHIVED,
                            Pageable.ofSize(1000)
                    )
                    .stream()
                    .filter(r -> name.equals(r.getName()))
                    .findFirst()
                    .orElse(null);

            if (existing != null && !existing.getId().equals(currentRouteId)) {
                throw new RouteValidationException("Маршрут с таким названием уже существует");
            }
        }
    }

    private void validateCreateRequest(RouteCreateRequest request) {
        if (request.getDays() == null || request.getDays().isEmpty()) {
            throw new RouteValidationException("Маршрут должен содержать хотя бы один день");
        }

        Set<Short> dayNumbers = new HashSet<>();
        for (RouteDayCreateRequest day : request.getDays()) {
            if (!dayNumbers.add(day.getDayNumber())) {
                throw new RouteValidationException("Номера дней должны быть уникальными");
            }

            if (day.getPoints() == null || day.getPoints().isEmpty()) {
                throw new RouteValidationException("Каждый день должен содержать хотя бы одну точку");
            }

            Set<Short> orders = new HashSet<>();
            for (RoutePointCreateRequest point : day.getPoints()) {
                if (!orders.add(point.getOrderIndex())) {
                    throw new RouteValidationException("Порядок точек должен быть уникальным внутри дня");
                }

                if (point.getPlannedArrival() != null
                        && point.getPlannedDeparture() != null
                        && point.getPlannedDeparture().isBefore(point.getPlannedArrival())) {
                    throw new RouteValidationException("plannedDepartureAt не может быть раньше plannedArrivalAt");
                }
            }
        }
    }

    private List<Long> extractPoiIds(RouteCreateRequest request) {
        return request.getDays().stream()
                .flatMap(day -> day.getPoints().stream())
                .map(RoutePointCreateRequest::getPoiId)
                .distinct()
                .toList();
    }

    private Map<Long, PoiResponse> fetchAndValidatePois(Long routeCityId, List<Long> poiIds) {
        List<PoiResponse> pois;
        try {
            pois = poiClient.getPoisBatch(poiIds);
        } catch (Exception e) {
            throw new RouteValidationException("Не удалось получить объекты из poi-service");
        }

        Map<Long, PoiResponse> poiMap = pois.stream()
                .collect(Collectors.toMap(PoiResponse::getId, Function.identity()));

        for (Long poiId : poiIds) {
            PoiResponse poi = poiMap.get(poiId);
            if (poi == null) {
                throw new RouteValidationException("POI " + poiId + " не найден");
            }
            validatePoiBelongsToCity(routeCityId, poi);
        }

        return poiMap;
    }

    private void validatePoiBelongsToCity(Long routeCityId, PoiResponse poi) {
        if (!Objects.equals(routeCityId, poi.getCityId())) {
            throw new RouteValidationException("POI " + poi.getId() + " не принадлежит городу маршрута");
        }
    }

    private void populateRouteDays(Route route, List<RouteDayCreateRequest> days, Map<Long, PoiResponse> poiMap) {
        List<RouteDayCreateRequest> sortedDays = days.stream()
                .sorted(Comparator.comparing(RouteDayCreateRequest::getDayNumber))
                .toList();

        for (RouteDayCreateRequest dayRequest : sortedDays) {
            RouteDay day = new RouteDay();
            day.setDayNumber(dayRequest.getDayNumber());
            day.setDescription(dayRequest.getDescription());
            day.setPlannedStart(dayRequest.getPlannedStart());
            day.setPlannedEnd(dayRequest.getPlannedEnd());
            route.addRouteDay(day);

            dayRequest.getPoints().stream()
                    .sorted(Comparator.comparing(RoutePointCreateRequest::getOrderIndex))
                    .forEach(pointRequest -> {
                        PoiResponse poi = poiMap.get(pointRequest.getPoiId());

                        RoutePoint point = new RoutePoint();
                        point.setOrderIndex(pointRequest.getOrderIndex());
                        point.setPoiId(pointRequest.getPoiId());
                        point.setEstimatedVisitMinutes(pointRequest.getEstimatedVisitMinutes());
                        point.setPlannedArrivalAt(pointRequest.getPlannedArrival());
                        point.setPlannedDepartureAt(pointRequest.getPlannedDeparture());

                        applyPoiSnapshot(point, poi);
                        day.addRoutePoint(point);
                    });
        }
    }

    private void applyPoiSnapshot(RoutePoint point, PoiResponse poi) {
        point.setPoiDetails(
                poi.getName(),
                poi.getAddress(),
                poi.getLatitude(),
                poi.getLongitude(),
                poi.getPoiType() != null ? poi.getPoiType().getCode() : null
        );
    }

    private RoutePoint copyPoint(RoutePoint sourcePoint) {
        RoutePoint point = new RoutePoint();
        point.setOrderIndex(sourcePoint.getOrderIndex());
        point.setPoiId(sourcePoint.getPoiId());
        point.setPoiName(sourcePoint.getPoiName());
        point.setPoiAddress(sourcePoint.getPoiAddress());
        point.setPoiLatitude(sourcePoint.getPoiLatitude());
        point.setPoiLongitude(sourcePoint.getPoiLongitude());
        point.setPoiType(sourcePoint.getPoiType());
        point.setEstimatedVisitMinutes(sourcePoint.getEstimatedVisitMinutes());
        point.setPlannedArrivalAt(sourcePoint.getPlannedArrivalAt());
        point.setPlannedDepartureAt(sourcePoint.getPlannedDepartureAt());
        return point;
    }

    private void shiftRoutePointsOrder(RouteDay day, short fromOrder) {
        day.getRoutePoints().stream()
                .filter(point -> point.getOrderIndex() >= fromOrder)
                .forEach(point -> point.setOrderIndex((short) (point.getOrderIndex() + 1)));
    }

    private short nextOrderIndex(RouteDay routeDay) {
        return routePointRepository.findMaxOrderIndexByRouteDayId(routeDay.getId())
                .map(max -> (short) (max + 1))
                .orElse((short) 1);
    }

    private RouteDay resolveRouteDay(Route route, Short dayNumber) {
        if (dayNumber != null) {
            return routeDayRepository.findByRouteIdAndDayNumber(route.getId(), dayNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("День маршрута не найден"));
        }

        return route.getRouteDays().stream()
                .max(Comparator.comparing(RouteDay::getDayNumber))
                .orElseThrow(() -> new ResourceNotFoundException("У маршрута нет дней"));
    }

    private void normalizeDayOrder(RouteDay day) {
        List<RoutePoint> points = day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .toList();

        for (int i = 0; i < points.size(); i++) {
            points.get(i).setOrderIndex((short) (i + 1));
        }
    }

    private void recalculateRouteMetrics(Route route) {
        double totalDistanceKm = 0.0;
        int totalTravelDurationMinutes = 0;
        int totalVisitDurationMinutes = 0;

        for (RouteDay day : route.getRouteDays()) {
            List<RoutePoint> points = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            for (RoutePoint point : points) {
                totalVisitDurationMinutes += Optional.ofNullable(point.getEstimatedVisitMinutes()).orElse(60);
            }

            routeDayPathRepository.findByRouteDayId(day.getId()).ifPresent(dayPath -> {
                if (dayPath.getDistanceKm() != null) {
                    // суммирование сделаем ниже без lambda-модификаций
                }
            });

            var dayPathOpt = routeDayPathRepository.findByRouteDayId(day.getId());
            if (dayPathOpt.isPresent()) {
                var dayPath = dayPathOpt.get();
                if (dayPath.getDistanceKm() != null) {
                    totalDistanceKm += dayPath.getDistanceKm().doubleValue();
                }
                if (dayPath.getDurationMin() != null) {
                    totalTravelDurationMinutes += dayPath.getDurationMin();
                }
            }

            fillPlannedTimes(day);
        }

        route.setDistanceKm(BigDecimal.valueOf(totalDistanceKm).setScale(2, RoundingMode.HALF_UP));
        route.setDurationMin(totalTravelDurationMinutes + totalVisitDurationMinutes);
        route.setStartPoint(firstPointName(route));
        route.setEndPoint(lastPointName(route));
    }

    private void fillPlannedTimes(RouteDay day) {
        LocalDateTime cursor = day.getPlannedStart();
        List<RoutePoint> points = day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .toList();

        for (RoutePoint point : points) {
            if (cursor == null) {
                break;
            }

            if (point.getPlannedArrivalAt() == null) {
                point.setPlannedArrivalAt(cursor);
            }

            if (point.getPlannedDepartureAt() == null) {
                point.setPlannedDepartureAt(
                        point.getPlannedArrivalAt().plusMinutes(
                                Optional.ofNullable(point.getEstimatedVisitMinutes()).orElse(60)
                        )
                );
            }

            cursor = point.getPlannedDepartureAt();
        }
    }

    private String firstPointName(Route route) {
        return route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .flatMap(day -> day.getRoutePoints().stream().sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .map(RoutePoint::getPoiName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Не указано");
    }

    private String lastPointName(Route route) {
        List<RoutePoint> allPoints = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .flatMap(day -> day.getRoutePoints().stream().sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .toList();

        return allPoints.isEmpty()
                ? "Не указано"
                : Optional.ofNullable(allPoints.get(allPoints.size() - 1).getPoiName()).orElse("Не указано");
    }

    private List<String> buildWarnings(Route route, Map<Long, PoiResponse> poiMap) {
        List<String> warnings = new ArrayList<>();
        Map<Long, PoiResponse> resolvedPoiMap = poiMap != null ? new HashMap<>(poiMap) : new HashMap<>();

        List<Long> missingPoiIds = route.getRouteDays().stream()
                .flatMap(day -> day.getRoutePoints().stream())
                .map(RoutePoint::getPoiId)
                .filter(Objects::nonNull)
                .filter(id -> !resolvedPoiMap.containsKey(id))
                .distinct()
                .toList();

        if (!missingPoiIds.isEmpty()) {
            try {
                List<PoiResponse> pois = poiClient.getPoisBatch(missingPoiIds);
                if (pois != null) {
                    for (PoiResponse poi : pois) {
                        if (poi != null && poi.getId() != null) {
                            resolvedPoiMap.put(poi.getId(), poi);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load POIs for warning analysis", e);
            }
        }

        for (RouteDay day : route.getRouteDays()) {
            int dayDuration = day.getRoutePoints().stream()
                    .mapToInt(p -> Optional.ofNullable(p.getEstimatedVisitMinutes()).orElse(60))
                    .sum();

            var dayPathOpt = routeDayPathRepository.findByRouteDayId(day.getId());
            if (dayPathOpt.isPresent() && dayPathOpt.get().getDurationMin() != null) {
                dayDuration += dayPathOpt.get().getDurationMin();
            }

            if (dayDuration > 12 * 60) {
                warnings.add("День " + day.getDayNumber() + " перегружен: около " + dayDuration + " минут");
            }

            LocalDate routeDate = day.getPlannedStart() != null
                    ? day.getPlannedStart().toLocalDate()
                    : (day.getPlannedEnd() != null ? day.getPlannedEnd().toLocalDate() : null);
            LocalTime dayStart = day.getPlannedStart() != null ? day.getPlannedStart().toLocalTime() : LocalTime.of(9, 0);
            LocalTime dayEnd = day.getPlannedEnd() != null ? day.getPlannedEnd().toLocalTime() : LocalTime.of(18, 0);

            List<RoutePoint> points = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            for (RoutePoint point : points) {
                PoiResponse poi = resolvedPoiMap.get(point.getPoiId());
                if (poi != null && Boolean.TRUE.equals(poi.getIsClosed())) {
                    warnings.add("POI \"" + poi.getName() + "\" отмечен как закрытый");
                    continue;
                }
                if (poi == null || routeDate == null || point.getPlannedArrivalAt() == null || point.getPlannedDepartureAt() == null) {
                    continue;
                }

                boolean withinWindow = isVisitWithinOpeningHours(poi, routeDate, dayStart, dayEnd, point.getPlannedArrivalAt(), point.getPlannedDepartureAt());
                if (!withinWindow) {
                    warnings.add("День " + day.getDayNumber() + ": \"" + Optional.ofNullable(point.getPoiName()).orElse("POI " + point.getPoiId()) + "\" выходит за рамки графика работы");
                }
            }
        }

        return warnings;
    }

    private boolean isVisitWithinOpeningHours(PoiResponse poi, LocalDate routeDate, LocalTime dayStart, LocalTime dayEnd,
                                              LocalDateTime arrival, LocalDateTime departure) {
        if (poi == null || Boolean.TRUE.equals(poi.getIsClosed())) {
            return false;
        }
        if (poi.getHours() == null || poi.getHours().isEmpty()) {
            return !departure.isAfter(routeDate.atTime(dayEnd));
        }

        DayOfWeek dayOfWeek = routeDate.getDayOfWeek();
        LocalDateTime dayOpen = routeDate.atTime(dayStart);
        LocalDateTime dayClose = routeDate.atTime(dayEnd);

        for (PoiResponse.PoiHoursDto hours : poi.getHours()) {
            if (hours == null || hours.getDayOfWeek() == null) {
                continue;
            }
            int apiDay = hours.getDayOfWeek();
            if (apiDay != dayOfWeek.getValue() && apiDay != (dayOfWeek.getValue() % 7)) {
                continue;
            }
            if (Boolean.TRUE.equals(hours.getAroundTheClock())) {
                return !arrival.isBefore(dayOpen) && !departure.isAfter(dayClose);
            }
            LocalTime openTime = parseWarningLocalTime(String.valueOf(hours.getOpenTime())).orElse(dayStart);
            LocalTime closeTime = parseWarningLocalTime(String.valueOf(hours.getCloseTime())).orElse(dayEnd);
            LocalDateTime openAt = routeDate.atTime(openTime);
            LocalDateTime closeAt = routeDate.atTime(closeTime);
            if (!closeAt.isAfter(openAt)) {
                closeAt = closeAt.plusDays(1);
            }
            LocalDateTime effectiveOpen = openAt.isAfter(dayOpen) ? openAt : dayOpen;
            LocalDateTime effectiveClose = closeAt.isBefore(dayClose) ? closeAt : dayClose;
            if (!effectiveClose.isAfter(effectiveOpen)) {
                continue;
            }
            if (!arrival.isBefore(effectiveOpen) && !departure.isAfter(effectiveClose)) {
                return true;
            }
        }
        return false;
    }

    private Optional<LocalTime> parseWarningLocalTime(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalTime.parse(value));
        } catch (DateTimeParseException e) {
            try {
                return Optional.of(LocalTime.parse(value.length() >= 5 ? value.substring(0, 5) : value));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
    }

    private RouteResponse toResponseWithWarnings(Route route, List<String> warnings) {
        RouteResponse response = routeMapper.toResponse(route);
        response.setWarnings(warnings);
        return response;
    }
}