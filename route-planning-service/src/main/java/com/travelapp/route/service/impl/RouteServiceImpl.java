package com.travelapp.route.service.impl;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.exception.RouteValidationException;
import com.travelapp.route.mapper.RouteMapper;
import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteDayRepository;
import com.travelapp.route.repository.RoutePointRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routeRepository;
    private final RouteDayRepository routeDayRepository;
    private final RoutePointRepository routePointRepository;
    private final RouteMapper routeMapper;
    private final PoiClient poiClient;
    private final RouteOptimizationService optimizationService;

    @Override
    @Transactional
    public RouteResponse createRoute(Long userId, RouteCreateRequest request) {
        log.info("Creating route for user {}: {}", userId, request.getName());

        // Проверка уникальности названия
        if (routeRepository.existsByUserIdAndNameAndIsArchived(userId, request.getName(), (short) 0)) {
            throw new RouteValidationException("Маршрут с таким названием уже существует");
        }

        // Создание маршрута
        Route route = routeMapper.toEntity(request);
        route.setUserId(userId);

        if (request.getStartDate() == null) {
            route.setStartPoint("Не указано");
            route.setEndPoint("Не указано");
        }

        Route savedRoute = routeRepository.save(route);

        // Создание дней маршрута
        int daysCount = Optional.ofNullable(request.getDaysCount()).orElse(1);
        createRouteDays(savedRoute, daysCount, request.getStartDate());

        // Загрузка доп. информации
        RouteResponse response = routeMapper.toResponse(savedRoute);
        enrichRouteWithPoiDetails(response);

        log.info("Route created successfully: {}", savedRoute.getId());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse getRouteById(Long userId, Long routeId) {
        log.info("Getting route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        RouteResponse response = routeMapper.toResponse(route);
        enrichRouteWithPoiDetails(response);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> getUserRoutes(Long userId, Pageable pageable) {
        log.info("Getting routes for user {}", userId);

        Page<Route> routes = routeRepository.findByUserId(userId, pageable);
        Page<RouteResponse> responses = routes.map(routeMapper::toResponse);

        responses.forEach(this::enrichRouteWithPoiDetails);

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RouteResponse> getArchivedRoutes(Long userId, Pageable pageable) {
        log.info("Getting archived routes for user {}", userId);

        Page<Route> routes = routeRepository.findArchivedByUserId(userId, pageable);
        Page<RouteResponse> responses = routes.map(routeMapper::toResponse);

        responses.forEach(this::enrichRouteWithPoiDetails);

        return responses;
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse updateRoute(Long userId, Long routeId, RouteUpdateRequest request) {
        log.info("Updating route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        if (request.getName() != null && !request.getName().equals(route.getName())) {
            if (routeRepository.existsByUserIdAndNameAndIsArchived(userId, request.getName(), route.getIsArchived())) {
                throw new RouteValidationException("Маршрут с таким названием уже существует");
            }
        }

        routeMapper.updateEntity(route, request);
        Route updatedRoute = routeRepository.save(route);

        RouteResponse response = routeMapper.toResponse(updatedRoute);
        enrichRouteWithPoiDetails(response);

        log.info("Route {} updated successfully", routeId);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void archiveRoute(Long userId, Long routeId) {
        log.info("Archiving route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        route.archive();
        routeRepository.save(route);

        log.info("Route {} archived successfully", routeId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void unarchiveRoute(Long userId, Long routeId) {
        log.info("Unarchiving route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        route.unarchive();
        routeRepository.save(route);

        log.info("Route {} unarchived successfully", routeId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public void deleteRoute(Long userId, Long routeId) {
        log.info("Deleting route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        routeRepository.delete(route);
        log.info("Route {} deleted successfully", routeId);
    }

    @Override
    @Transactional
    public RouteResponse duplicateRoute(Long userId, Long routeId, String newName) {
        log.info("Duplicating route {} for user {} with new name: {}", routeId, userId, newName);

        Route originalRoute = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // Проверка имени для дубликата
        String duplicateName = newName != null ? newName : originalRoute.getName() + " (копия)";
        if (routeRepository.existsByUserIdAndNameAndIsArchived(userId, duplicateName, (short) 0)) {
            throw new RouteValidationException("Маршрут с таким названием уже существует");
        }

        // Создание дубликата
        Route duplicate = new Route();
        duplicate.setName(duplicateName);
        duplicate.setDescription(originalRoute.getDescription());
        duplicate.setCoverPhotoUrl(originalRoute.getCoverPhotoUrl());
        duplicate.setTransportMode(originalRoute.getTransportMode());
        duplicate.setUserId(userId);
        duplicate.setCityId(originalRoute.getCityId());
        duplicate.setStartPoint(originalRoute.getStartPoint());
        duplicate.setEndPoint(originalRoute.getEndPoint());

        Route savedDuplicate = routeRepository.save(duplicate);

        // Дублирование дней и точек
        duplicateRouteDays(originalRoute, savedDuplicate);

        RouteResponse response = routeMapper.toResponse(savedDuplicate);
        enrichRouteWithPoiDetails(response);

        log.info("Route duplicated successfully: {}", savedDuplicate.getId());
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse addPoiToRoute(Long userId, Long routeId, Long poiId, Short dayNumber, Short orderIndex) {
        log.info("Adding POI {} to route {} for user {}", poiId, routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // Проверка существования POI
        PoiResponse poi = poiClient.getPoiById(poiId)
                .orElseThrow(() -> new ResourceNotFoundException("Объект не найден"));

        // Получение дня маршрута
        RouteDay routeDay;
        if (dayNumber != null) {
            routeDay = routeDayRepository.findByRouteIdAndDayNumber(routeId, dayNumber)
                    .orElseThrow(() -> new ResourceNotFoundException("День маршрута не найден"));
        } else {
            // Добавление в последний день или создание нового
            Short maxDay = routeDayRepository.findMaxDayNumberByRouteId(routeId).orElse((short) 1);
            routeDay = routeDayRepository.findByRouteIdAndDayNumber(routeId, maxDay)
                    .orElseGet(() -> createNewRouteDay(route, maxDay));
        }

        // Проверка, не добавлен ли уже этот POI в этот день
        if (routePointRepository.existsByRouteDayIdAndPoiId(routeDay.getId(), poiId)) {
            throw new RouteValidationException("Объект уже добавлен в этот день маршрута");
        }

        // Определение orderIndex
        Short actualOrderIndex = orderIndex;
        if (actualOrderIndex == null) {
            actualOrderIndex = routePointRepository.findMaxOrderIndexByRouteDayId(routeDay.getId())
                    .map(max -> (short) (max + 1))
                    .orElse((short) 1);
        } else {
            // Сдвиг существующих точек
            shiftRoutePointsOrder(routeDay.getId(), actualOrderIndex);
        }

        // Создание точки маршрута
        RoutePoint routePoint = new RoutePoint();
        routePoint.setOrderIndex(actualOrderIndex);
        routePoint.setPoiId(poiId);
        routePoint.setRouteDay(routeDay);

        routePoint.setPoiDetails(
                poi.getName(),
                poi.getAddress(),
                poi.getLatitude(),
                poi.getLongitude(),
                poi.getType()
        );

        routePointRepository.save(routePoint);
        routeDay.addRoutePoint(routePoint);

        // Обновление информации о маршруте
        updateRouteStatistics(route);

        RouteResponse response = routeMapper.toResponse(route);
        enrichRouteWithPoiDetails(response);

        log.info("POI {} added to route {} successfully", poiId, routeId);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse removePoiFromRoute(Long userId, Long routeId, Long poiId) {
        log.info("Removing POI {} from route {} for user {}", poiId, routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // Поиск точки по POI ID во всех днях маршрута
        List<RoutePoint> pointsToRemove = routePointRepository.findByPoiId(poiId).stream()
                .filter(point -> point.getRouteDay().getRoute().getId().equals(routeId))
                .toList();

        if (pointsToRemove.isEmpty()) {
            throw new ResourceNotFoundException("Объект не найден в маршруте");
        }

        // Удаление точек и пересчет orderIndex
        for (RoutePoint point : pointsToRemove) {
            RouteDay day = point.getRouteDay();
            routePointRepository.delete(point);

            // Пересчет orderIndex для оставшихся точек в этом дне
            reorderPointsInDay(day);
        }

        // Обновление информации о маршруте
        updateRouteStatistics(route);

        RouteResponse response = routeMapper.toResponse(route);
        enrichRouteWithPoiDetails(response);

        log.info("POI {} removed from route {} successfully", poiId, routeId);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse optimizeRoute(Long userId, Long routeId, String optimizationMode) {
        log.info("Optimizing route {} for user {} with mode: {}", routeId, userId, optimizationMode);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // Выполнение оптимизации
        Route optimizedRoute = optimizationService.optimizeRoute(route, optimizationMode);

        // Сохранение оптимизированного маршрута
        optimizedRoute.setIsOptimized(true);
        optimizedRoute.setOptimizationMode(optimizationMode);

        Route savedRoute = routeRepository.save(optimizedRoute);

        RouteResponse response = routeMapper.toResponse(savedRoute);
        enrichRouteWithPoiDetails(response);

        log.info("Route {} optimized successfully with mode: {}", routeId, optimizationMode);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RouteResponse> getRoutesByCity(Long userId, Long cityId) {
        log.info("Getting routes for user {} in city {}", userId, cityId);

        List<Route> routes = routeRepository.findByUserIdAndCityId(userId, cityId);
        List<RouteResponse> responses = routeMapper.toResponseList(routes);

        responses.forEach(this::enrichRouteWithPoiDetails);

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public long countUserRoutes(Long userId) {
        return routeRepository.countActiveRoutesByUserId(userId);
    }

    @Override
    public boolean isRouteNameAvailable(Long userId, String name) {
        return !routeRepository.existsByUserIdAndNameAndIsArchived(userId, name, (short) 0);
    }

    // Вспомогательные методы

    private void createRouteDays(Route route, int daysCount, LocalDateTime startDate) {
        for (int i = 1; i <= daysCount; i++) {
            RouteDay day = new RouteDay();
            day.setDayNumber((short) i);
            day.setRoute(route);

            if (startDate != null) {
                LocalDateTime dayStart = startDate.plusDays(i - 1);
                day.setPlannedStart(dayStart);
                day.setPlannedEnd(dayStart.plusHours(8)); // 8-часовой день по умолчанию
                day.setDescription("День " + i);
            }

            route.addRouteDay(day);
        }
    }

    private RouteDay createNewRouteDay(Route route, Short dayNumber) {
        RouteDay day = new RouteDay();
        day.setDayNumber(dayNumber);
        day.setRoute(route);
        day.setDescription("День " + dayNumber);

        if (route.getRouteDays().isEmpty()) {
            day.setPlannedStart(LocalDateTime.now().with(LocalTime.of(9, 0)));
            day.setPlannedEnd(LocalDateTime.now().with(LocalTime.of(18, 0)));
        } else {
            RouteDay lastDay = route.getRouteDays().get(route.getRouteDays().size() - 1);
            day.setPlannedStart(lastDay.getPlannedStart().plusDays(1));
            day.setPlannedEnd(lastDay.getPlannedEnd().plusDays(1));
        }

        return routeDayRepository.save(day);
    }

    private void duplicateRouteDays(Route sourceRoute, Route targetRoute) {
        for (RouteDay sourceDay : sourceRoute.getRouteDays()) {
            RouteDay targetDay = new RouteDay();
            targetDay.setDayNumber(sourceDay.getDayNumber());
            targetDay.setDescription(sourceDay.getDescription());
            targetDay.setPlannedStart(sourceDay.getPlannedStart());
            targetDay.setPlannedEnd(sourceDay.getPlannedEnd());
            targetDay.setRoute(targetRoute);

            targetRoute.addRouteDay(targetDay);

            // Дублирование точек
            for (RoutePoint sourcePoint : sourceDay.getRoutePoints()) {
                RoutePoint targetPoint = new RoutePoint();
                targetPoint.setOrderIndex(sourcePoint.getOrderIndex());
                targetPoint.setPoiId(sourcePoint.getPoiId());
                targetPoint.setRouteDay(targetDay);

                targetDay.addRoutePoint(targetPoint);
            }
        }
    }

    private void shiftRoutePointsOrder(Long routeDayId, Short fromOrder) {
        List<RoutePoint> points = routePointRepository.findByRouteDayIdOrderByOrderIndexAsc(routeDayId);

        for (RoutePoint point : points) {
            if (point.getOrderIndex() >= fromOrder) {
                point.setOrderIndex((short) (point.getOrderIndex() + 1));
            }
        }

        routePointRepository.saveAll(points);
    }

    private void reorderPointsInDay(RouteDay day) {
        List<RoutePoint> points = day.getRoutePoints().stream()
                .sorted((p1, p2) -> Short.compare(p1.getOrderIndex(), p2.getOrderIndex()))
                .toList();

        for (int i = 0; i < points.size(); i++) {
            points.get(i).setOrderIndex((short) (i + 1));
        }

        routePointRepository.saveAll(points);
    }

    private void updateRouteStatistics(Route route) {
        // Расчет общей продолжительности и расстояния
        int totalDuration = 0;
        double totalDistance = 0.0;

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null) {
                totalDuration += day.getRoutePoints().stream()
                        .mapToInt(point -> point.getEstimatedDuration() != null ? point.getEstimatedDuration() : 60)
                        .sum();

                // Здесь будет логика расчета расстояния между точками
                // Пока используем приблизительное значение
                totalDistance += day.getRoutePoints().size() * 1.5; // ~1.5 км между точками
            }
        }

        route.setDurationMin(totalDuration);
        route.setDistanceKm(totalDistance);

        routeRepository.save(route);
    }

    private void enrichRouteWithPoiDetails(RouteResponse response) {
        if (response.getRouteDays() != null) {
            response.getRouteDays().forEach(day -> {
                if (day.getRoutePoints() != null) {
                    day.getRoutePoints().forEach(point -> {
                        try {
                            PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                            if (poi != null) {
                                point.setPoiName(poi.getName());
                                point.setPoiAddress(poi.getAddress());
                                point.setPoiLatitude(poi.getLatitude());
                                point.setPoiLongitude(poi.getLongitude());
                                point.setPoiType(poi.getType());
                            }
                        } catch (Exception e) {
                            log.warn("Failed to fetch POI details for ID: {}", point.getPoiId(), e);
                        }
                    });
                }
            });
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "routes", key = "#userId + '_' + #routeId")
    public RouteResponse reorderRoutePoints(Long userId, Long routeId, List<Long> pointIdsInOrder) {
        log.info("Reordering points in route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // Проверка, что все точки принадлежат маршруту
        List<RoutePoint> allPoints = routePointRepository.findByRouteId(routeId);
        if (pointIdsInOrder.size() != allPoints.size() ||
                !allPoints.stream().map(RoutePoint::getId).allMatch(pointIdsInOrder::contains)) {
            throw new RouteValidationException("Некорректный список точек для сортировки");
        }

        // Перезапись orderIndex
        for (int i = 0; i < pointIdsInOrder.size(); i++) {
            Long pointId = pointIdsInOrder.get(i);
            RoutePoint point = allPoints.stream()
                    .filter(p -> p.getId().equals(pointId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Точка маршрута не найдена"));

            point.setOrderIndex((short) (i + 1));
        }

        routePointRepository.saveAll(allPoints);

        RouteResponse response = routeMapper.toResponse(route);
        enrichRouteWithPoiDetails(response);

        log.info("Points in route {} reordered successfully", routeId);
        return response;
    }
}