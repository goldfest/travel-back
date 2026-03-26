package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private final PoiClient poiClient;
    private final DistanceCalculationService distanceService;

    public Route optimizeRoute(Route route, String optimizationMode) {
        String mode = optimizationMode == null ? "TIME" : optimizationMode.toUpperCase(Locale.ROOT);
        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() == null || day.getRoutePoints().size() <= 1) {
                continue;
            }
            List<RoutePoint> reordered = switch (mode) {
                case "DISTANCE", "TIME", "SCENIC", "RATING" -> nearestNeighborOrder(day.getRoutePoints());
                default -> nearestNeighborOrder(day.getRoutePoints());
            };
            day.getRoutePoints().clear();
            day.getRoutePoints().addAll(reordered);
            updateOrderIndices(day);
        }
        calculateRouteStatistics(route);
        route.setIsOptimized(true);
        route.setOptimizationMode(mode);
        return route;
    }

    private List<RoutePoint> nearestNeighborOrder(List<RoutePoint> source) {
        List<RoutePoint> unvisited = new ArrayList<>(source);
        List<RoutePoint> ordered = new ArrayList<>();
        RoutePoint current = unvisited.remove(0);
        ordered.add(current);
        while (!unvisited.isEmpty()) {
            RoutePoint nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (RoutePoint candidate : unvisited) {
                double distance = distanceService.calculateDistance(coordinates(current), coordinates(candidate));
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            if (nearest == null) {
                nearest = unvisited.get(0);
            }
            unvisited.remove(nearest);
            ordered.add(nearest);
            current = nearest;
        }
        return ordered;
    }

    private double[] coordinates(RoutePoint point) {
        if (point.getPoiLatitude() != null && point.getPoiLongitude() != null) {
            return new double[]{point.getPoiLatitude(), point.getPoiLongitude()};
        }
        try {
            PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
            if (poi != null && poi.getLatitude() != null && poi.getLongitude() != null) {
                point.setPoiLatitude(poi.getLatitude());
                point.setPoiLongitude(poi.getLongitude());
                if (point.getPoiName() == null) {
                    point.setPoiName(poi.getName());
                    point.setPoiAddress(poi.getAddress());
                    point.setPoiType(poi.getType());
                }
                return new double[]{poi.getLatitude(), poi.getLongitude()};
            }
        } catch (Exception e) {
            log.warn("Failed to load coordinates for POI {} from poi-service, using snapshot/fallback", point.getPoiId());
        }
        return new double[]{0.0, 0.0};
    }

    private void updateOrderIndices(RouteDay day) {
        for (int i = 0; i < day.getRoutePoints().size(); i++) {
            day.getRoutePoints().get(i).setOrderIndex((short) (i + 1));
        }
    }

    private void calculateRouteStatistics(Route route) {
        double totalDistance = 0.0;
        int totalDuration = 0;
        for (RouteDay day : route.getRouteDays()) {
            List<RoutePoint> points = day.getRoutePoints();
            for (int i = 0; i < points.size(); i++) {
                totalDuration += Optional.ofNullable(points.get(i).getEstimatedVisitMinutes()).orElse(60);
                if (i > 0) {
                    double distance = distanceService.calculateDistance(coordinates(points.get(i - 1)), coordinates(points.get(i)));
                    totalDistance += distance;
                    totalDuration += distanceService.calculateTravelTime(distance, route.getTransportMode().name());
                }
            }
        }
        route.setDistanceKm(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP));
        route.setDurationMin(totalDuration);
    }
}
