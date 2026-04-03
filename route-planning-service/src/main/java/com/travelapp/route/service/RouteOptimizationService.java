package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private final PoiClient poiClient;
    private final RoutingProvider routingProvider;

    public Route optimizeRoute(Route route, String optimizationMode) {
        String mode = optimizationMode == null ? "TIME" : optimizationMode.toUpperCase(Locale.ROOT);

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() == null || day.getRoutePoints().size() <= 1) {
                continue;
            }

            List<RoutePoint> points = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            hydrateCoordinates(points);

            List<RoutePoint> reordered = switch (mode) {
                case "DISTANCE", "TIME", "SCENIC", "RATING" -> optimizeWithMatrix(points, route.getTransportMode(), mode);
                default -> optimizeWithMatrix(points, route.getTransportMode(), "TIME");
            };

            day.getRoutePoints().clear();
            day.getRoutePoints().addAll(reordered);
            updateOrderIndices(day);
        }

        route.setIsOptimized(true);
        route.setOptimizationMode(mode);
        return route;
    }

    private List<RoutePoint> optimizeWithMatrix(List<RoutePoint> points, Route.TransportMode transportMode, String mode) {
        List<RoutingPoint> routingPoints = points.stream()
                .map(point -> new RoutingPoint(
                        point.getId(),
                        safeLatitude(point),
                        safeLongitude(point)
                ))
                .toList();

        TravelMatrixResult matrix = routingProvider.buildMatrix(routingPoints, transportMode);
        List<Integer> order = nearestNeighborOrder(matrix, mode);

        List<RoutePoint> result = new ArrayList<>();
        for (Integer index : order) {
            result.add(points.get(index));
        }
        return result;
    }

    private List<Integer> nearestNeighborOrder(TravelMatrixResult matrix, String optimizationMode) {
        int n = matrix.getDurationMin().length;
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>();

        int current = 0;
        visited[current] = true;
        order.add(current);

        for (int step = 1; step < n; step++) {
            int bestNext = -1;
            double bestCost = Double.MAX_VALUE;

            for (int candidate = 0; candidate < n; candidate++) {
                if (visited[candidate]) {
                    continue;
                }

                double cost = switch (optimizationMode.toUpperCase(Locale.ROOT)) {
                    case "DISTANCE" -> matrix.getDistanceKm()[current][candidate];
                    case "TIME", "SCENIC", "RATING" -> matrix.getDurationMin()[current][candidate];
                    default -> matrix.getDurationMin()[current][candidate];
                };

                if (cost < bestCost) {
                    bestCost = cost;
                    bestNext = candidate;
                }
            }

            if (bestNext == -1) {
                break;
            }

            visited[bestNext] = true;
            order.add(bestNext);
            current = bestNext;
        }

        return order;
    }

    private void hydrateCoordinates(List<RoutePoint> points) {
        for (RoutePoint point : points) {
            if (point.getPoiLatitude() != null && point.getPoiLongitude() != null) {
                continue;
            }

            try {
                PoiResponse poi = poiClient.getPoiById(point.getPoiId());
                if (poi != null && poi.getLatitude() != null && poi.getLongitude() != null) {
                    point.setPoiLatitude(poi.getLatitude());
                    point.setPoiLongitude(poi.getLongitude());

                    if (point.getPoiName() == null) {
                        point.setPoiName(poi.getName());
                        point.setPoiAddress(poi.getAddress());
                        point.setPoiType(extractPoiType(poi));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load coordinates for POI {} from poi-service", point.getPoiId(), e);
            }
        }
    }

    private double safeLatitude(RoutePoint point) {
        return point.getPoiLatitude() != null ? point.getPoiLatitude() : 0.0;
    }

    private double safeLongitude(RoutePoint point) {
        return point.getPoiLongitude() != null ? point.getPoiLongitude() : 0.0;
    }

    private String extractPoiType(PoiResponse poi) {
        return poi != null && poi.getPoiType() != null ? poi.getPoiType().getCode() : null;
    }

    private void updateOrderIndices(RouteDay day) {
        List<RoutePoint> sorted = day.getRoutePoints().stream()
                .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setOrderIndex((short) (i + 1));
        }

        day.getRoutePoints().sort(Comparator.comparing(RoutePoint::getOrderIndex));
    }
}