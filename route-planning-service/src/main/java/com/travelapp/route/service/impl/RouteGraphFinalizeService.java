package com.travelapp.route.service.impl;

import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteDayRepository;
import com.travelapp.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RouteGraphFinalizeService {

    private final RouteRepository routeRepository;
    private final RouteDayRepository routeDayRepository;
    private final RouteDayPathRepository routeDayPathRepository;

    @Transactional
    public void finalizePreparedRoute(Long routeId) {
        Route route = routeRepository.findWithDaysById(routeId)
                .orElseThrow(() -> new IllegalArgumentException("Маршрут не найден: " + routeId));

        List<RouteDay> routeDays = routeDayRepository.findWithPointsByRouteId(routeId);
        route.setRouteDays(routeDays);

        recalculateRouteMetrics(route);

        if (route.getStatus() == Route.RouteStatus.GRAPH_PREPARING) {
            route.setStatus(Route.RouteStatus.READY);
        }

        routeRepository.save(route);
    }

    @Transactional
    public void markRoutePreparationFailed(Long routeId) {
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
            List<RoutePoint> sortedPoints = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            for (RoutePoint point : sortedPoints) {
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

            fillPlannedTimes(day, sortedPoints);
        }

        route.setDistanceKm(BigDecimal.valueOf(totalDistanceKm).setScale(2, RoundingMode.HALF_UP));
        route.setDurationMin(totalTravelDurationMinutes + totalVisitDurationMinutes);
        route.setStartPoint(firstPointName(route));
        route.setEndPoint(lastPointName(route));
    }

    private void fillPlannedTimes(RouteDay day, List<RoutePoint> sortedPoints) {
        LocalDateTime cursor = day.getPlannedStart();

        for (RoutePoint point : sortedPoints) {
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
                .flatMap(day -> day.getRoutePoints().stream()
                        .sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .map(RoutePoint::getPoiName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Не указано");
    }

    private String lastPointName(Route route) {
        return route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .flatMap(day -> day.getRoutePoints().stream()
                        .sorted(Comparator.comparing(RoutePoint::getOrderIndex)))
                .map(RoutePoint::getPoiName)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .orElse("Не указано");
    }
}