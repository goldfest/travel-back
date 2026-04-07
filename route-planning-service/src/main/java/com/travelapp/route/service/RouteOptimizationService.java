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

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private static final String MODE_TIME = "TIME";
    private static final String MODE_DISTANCE = "DISTANCE";

    private final PoiClient poiClient;
    private final RoutingProvider routingProvider;

    public Route optimizeRoute(Route route, String optimizationMode) {
        String mode = normalizeMode(optimizationMode);

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() == null || day.getRoutePoints().size() <= 2) {
                updateOrderIndices(day);
                continue;
            }

            List<RoutePoint> points = day.getRoutePoints().stream()
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            hydrateCoordinates(points);

            List<RoutePoint> optimizable = new ArrayList<>(points);
            RoutePoint fixedStart = optimizable.remove(0);
            RoutePoint fixedEnd = optimizable.remove(optimizable.size() - 1);

            if (optimizable.isEmpty()) {
                updateOrderIndices(day);
                continue;
            }

            List<RoutingPoint> routingPoints = optimizable.stream()
                    .map(point -> new RoutingPoint(
                            point.getId(),
                            point.getPoiId(),
                            safeLatitude(point),
                            safeLongitude(point)
                    ))
                    .toList();

            TravelMatrixResult matrix = routingProvider.buildMatrix(route.getCityId(), routingPoints, route.getTransportMode());
            int[] order = buildBestOrder(matrix, mode);

            List<RoutePoint> reordered = new ArrayList<>(points.size());
            reordered.add(fixedStart);
            for (int index : order) {
                reordered.add(optimizable.get(index));
            }
            reordered.add(fixedEnd);

            day.getRoutePoints().clear();
            day.getRoutePoints().addAll(reordered);
            updateOrderIndices(day);
        }

        route.setIsOptimized(true);
        route.setOptimizationMode(mode);
        return route;
    }

    private int[] buildBestOrder(TravelMatrixResult matrix, String mode) {
        int n = matrix.getDurationMin().length;
        if (n <= 1) {
            return buildIdentity(n);
        }
        if (n <= 10) {
            return exactHeldKarpOpenPath(matrix, mode);
        }
        return twoOpt(nearestNeighborOrder(matrix, mode), matrix, mode);
    }

    private int[] exactHeldKarpOpenPath(TravelMatrixResult matrix, String mode) {
        int n = matrix.getDurationMin().length;
        int size = 1 << n;
        double[][] dp = new double[size][n];
        int[][] parent = new int[size][n];

        for (double[] row : dp) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int[] row : parent) {
            Arrays.fill(row, -1);
        }

        for (int i = 0; i < n; i++) {
            dp[1 << i][i] = 0.0;
        }

        for (int mask = 1; mask < size; mask++) {
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0 || Double.isInfinite(dp[mask][last])) {
                    continue;
                }
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) {
                        continue;
                    }
                    int nextMask = mask | (1 << next);
                    double candidate = dp[mask][last] + travelCost(matrix, last, next, mode);
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
        int cursor = bestLast;
        int mask = fullMask;
        for (int pos = n - 1; pos >= 0; pos--) {
            order[pos] = cursor;
            int previous = parent[mask][cursor];
            mask ^= (1 << cursor);
            cursor = previous;
            if (cursor == -1 && pos > 0) {
                cursor = Integer.numberOfTrailingZeros(mask);
            }
        }
        return order;
    }

    private int[] nearestNeighborOrder(TravelMatrixResult matrix, String mode) {
        int n = matrix.getDurationMin().length;
        boolean[] visited = new boolean[n];
        int[] order = new int[n];
        int current = 0;
        order[0] = current;
        visited[current] = true;

        for (int step = 1; step < n; step++) {
            int bestNext = -1;
            double bestCost = Double.POSITIVE_INFINITY;
            for (int candidate = 0; candidate < n; candidate++) {
                if (visited[candidate]) {
                    continue;
                }
                double cost = travelCost(matrix, current, candidate, mode);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestNext = candidate;
                }
            }
            if (bestNext == -1) {
                break;
            }
            visited[bestNext] = true;
            order[step] = bestNext;
            current = bestNext;
        }
        return order;
    }

    private int[] twoOpt(int[] initialOrder, TravelMatrixResult matrix, String mode) {
        int[] best = Arrays.copyOf(initialOrder, initialOrder.length);
        boolean improved = true;

        while (improved) {
            improved = false;
            for (int i = 1; i < best.length - 1; i++) {
                for (int k = i + 1; k < best.length; k++) {
                    int[] candidate = twoOptSwap(best, i, k);
                    if (routeCost(candidate, matrix, mode) + 1e-6 < routeCost(best, matrix, mode)) {
                        best = candidate;
                        improved = true;
                    }
                }
            }
        }

        return best;
    }

    private int[] twoOptSwap(int[] order, int i, int k) {
        int[] result = new int[order.length];
        System.arraycopy(order, 0, result, 0, i);
        for (int c = i; c <= k; c++) {
            result[c] = order[k - (c - i)];
        }
        if (k + 1 < order.length) {
            System.arraycopy(order, k + 1, result, k + 1, order.length - (k + 1));
        }
        return result;
    }

    private double routeCost(int[] order, TravelMatrixResult matrix, String mode) {
        double cost = 0.0;
        for (int i = 1; i < order.length; i++) {
            cost += travelCost(matrix, order[i - 1], order[i], mode);
        }
        return cost;
    }

    private double travelCost(TravelMatrixResult matrix, int from, int to, String mode) {
        return MODE_DISTANCE.equals(mode)
                ? matrix.getDistanceKm()[from][to]
                : matrix.getDurationMin()[from][to];
    }

    private int[] buildIdentity(int size) {
        int[] order = new int[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
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

    private String normalizeMode(String optimizationMode) {
        if (optimizationMode == null || optimizationMode.isBlank()) {
            return MODE_TIME;
        }
        String normalized = optimizationMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case MODE_TIME, MODE_DISTANCE -> normalized;
            default -> MODE_TIME;
        };
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
        List<RoutePoint> sorted = new ArrayList<>(day.getRoutePoints());
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setOrderIndex((short) (i + 1));
        }
        day.getRoutePoints().sort(Comparator.comparing(RoutePoint::getOrderIndex));
    }
}
