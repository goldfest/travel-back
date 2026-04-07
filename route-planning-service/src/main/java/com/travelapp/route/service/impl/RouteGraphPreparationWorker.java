package com.travelapp.route.service.impl;

import com.travelapp.route.client.GraphImportClient;
import com.travelapp.route.model.dto.internal.GraphImportTriggerRequest;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.RoutePathCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteGraphPreparationWorker {

    private static final Duration GRAPH_WAIT_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration GRAPH_WAIT_POLL_INTERVAL = Duration.ofSeconds(2);

    private final GraphImportClient graphImportClient;
    private final GraphVersionService graphVersionService;
    private final RouteRepository routeRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RoutePathCacheService routePathCacheService;

    @Async
    public void prepareGraphAndRoute(Long routeId, Long cityId) {
        try {
            ensureActiveGraphReady(cityId);

            routePathCacheService.rebuildRoutePaths(routeId);

            finalizePreparedRoute(routeId);
            log.info("Route graph preparation finished successfully for routeId={}, cityId={}", routeId, cityId);
        } catch (Exception ex) {
            log.error("Route graph preparation failed for routeId={}, cityId={}", routeId, cityId, ex);
            markRoutePreparationFailed(routeId);
        }
    }

    private void ensureActiveGraphReady(Long cityId) {
        if (graphVersionService.hasActiveVersion(cityId)) {
            return;
        }

        log.info("No active graph for city {}, starting async graph import", cityId);
        graphImportClient.importCityGraph(new GraphImportTriggerRequest(cityId, null));

        long deadline = System.currentTimeMillis() + GRAPH_WAIT_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (graphVersionService.hasActiveVersion(cityId)) {
                log.info("Active graph became available for cityId={}", cityId);
                return;
            }

            try {
                Thread.sleep(GRAPH_WAIT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ожидание подготовки графа было прервано", interruptedException);
            }
        }

        throw new IllegalStateException("Не удалось дождаться активной версии графа для города " + cityId);
    }

    @Transactional
    protected void finalizePreparedRoute(Long routeId) {
        routeRepository.findById(routeId).ifPresent(route -> {
            recalculateRouteMetrics(route);
            if (route.getStatus() == Route.RouteStatus.GRAPH_PREPARING) {
                route.setStatus(Route.RouteStatus.READY);
            }
            routeRepository.save(route);
        });
    }

    @Transactional
    protected void markRoutePreparationFailed(Long routeId) {
        routeRepository.findById(routeId).ifPresent(route -> {
            if (route.getStatus() == Route.RouteStatus.GRAPH_PREPARING) {
                route.setStatus(Route.RouteStatus.DRAFT);
                routeRepository.save(route);
            }
        });
    }

    private void recalculateRouteMetrics(Route route) {
        double totalDistanceKm = 0.0;
        int totalTravelDurationMinutes = 0;
        int totalVisitDurationMinutes = 0;

        for (RouteDay day : route.getRouteDays()) {
            for (RoutePoint point : day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList()) {
                totalVisitDurationMinutes += Optional.ofNullable(point.getEstimatedVisitMinutes()).orElse(60);
            }

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
        for (RoutePoint point : day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .toList()) {
            if (cursor == null) {
                break;
            }

            if (point.getPlannedArrivalAt() == null) {
                point.setPlannedArrivalAt(cursor);
            }

            if (point.getPlannedDepartureAt() == null) {
                point.setPlannedDepartureAt(
                        point.getPlannedArrivalAt().plusMinutes(Optional.ofNullable(point.getEstimatedVisitMinutes()).orElse(60))
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
        return route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .flatMap(day -> day.getRoutePoints().stream().sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .map(RoutePoint::getPoiName)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("Не указано");
    }
}
