package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteOptimizationService {

    private final PoiClient poiClient;
    private final DistanceCalculationService distanceService;

    public Route optimizeRoute(Route route, String optimizationMode) {
        log.info("Optimizing route {} with mode: {}", route.getId(), optimizationMode);

        switch (optimizationMode.toUpperCase()) {
            case "TIME":
                return optimizeForTime(route);
            case "DISTANCE":
                return optimizeForDistance(route);
            case "SCENIC":
                return optimizeForScenicRoute(route);
            case "RATING":
                return optimizeForHighRating(route);
            default:
                log.warn("Unknown optimization mode: {}, using TIME optimization", optimizationMode);
                return optimizeForTime(route);
        }
    }

    private Route optimizeForTime(Route route) {
        log.debug("Optimizing route {} for minimum time", route.getId());

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null && day.getRoutePoints().size() > 1) {
                List<RoutePoint> optimizedPoints = optimizeDayForTime(day);
                day.getRoutePoints().clear();
                day.getRoutePoints().addAll(optimizedPoints);
                updateOrderIndices(day);
            }
        }

        calculateRouteStatistics(route);
        return route;
    }

    private Route optimizeForDistance(Route route) {
        log.debug("Optimizing route {} for minimum distance", route.getId());

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null && day.getRoutePoints().size() > 1) {
                List<RoutePoint> optimizedPoints = optimizeDayForDistance(day);
                day.getRoutePoints().clear();
                day.getRoutePoints().addAll(optimizedPoints);
                updateOrderIndices(day);
            }
        }

        calculateRouteStatistics(route);
        return route;
    }

    private Route optimizeForScenicRoute(Route route) {
        log.debug("Optimizing route {} for scenic value", route.getId());

        // Получаем информацию о POI для оценки "живописности"
        Map<Long, PoiResponse> poiCache = new HashMap<>();

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null) {
                for (RoutePoint point : day.getRoutePoints()) {
                    poiCache.computeIfAbsent(point.getPoiId(), poiId ->
                            poiClient.getPoiById(poiId).orElse(null));
                }

                // Сортируем точки по "живописности" (предполагаем, что музеи и парки более живописны)
                day.getRoutePoints().sort((p1, p2) -> {
                    PoiResponse poi1 = poiCache.get(p1.getPoiId());
                    PoiResponse poi2 = poiCache.get(p2.getPoiId());

                    int scenicScore1 = calculateScenicScore(poi1);
                    int scenicScore2 = calculateScenicScore(poi2);

                    return Integer.compare(scenicScore2, scenicScore1); // По убыванию
                });

                updateOrderIndices(day);
            }
        }

        calculateRouteStatistics(route);
        return route;
    }

    private Route optimizeForHighRating(Route route) {
        log.debug("Optimizing route {} for high ratings", route.getId());

        Map<Long, PoiResponse> poiCache = new HashMap<>();

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null) {
                for (RoutePoint point : day.getRoutePoints()) {
                    poiCache.computeIfAbsent(point.getPoiId(), poiId ->
                            poiClient.getPoiById(poiId).orElse(null));
                }

                // Сортируем точки по рейтингу (по убыванию)
                day.getRoutePoints().sort((p1, p2) -> {
                    PoiResponse poi1 = poiCache.get(p1.getPoiId());
                    PoiResponse poi2 = poiCache.get(p2.getPoiId());

                    double rating1 = poi1 != null ? poi1.getAverageRating() : 0.0;
                    double rating2 = poi2 != null ? poi2.getAverageRating() : 0.0;

                    return Double.compare(rating2, rating1);
                });

                updateOrderIndices(day);
            }
        }

        calculateRouteStatistics(route);
        return route;
    }

    private List<RoutePoint> optimizeDayForTime(RouteDay day) {
        if (day.getRoutePoints() == null || day.getRoutePoints().size() <= 2) {
            return new ArrayList<>(day.getRoutePoints());
        }

        // Реализация алгоритма ближайшего соседа для минимизации времени
        List<RoutePoint> points = new ArrayList<>(day.getRoutePoints());
        List<RoutePoint> optimized = new ArrayList<>();

        // Получаем координаты для расчета расстояний
        Map<Long, double[]> coordinates = getCoordinatesForPoints(points);

        // Начинаем с первой точки
        RoutePoint current = points.remove(0);
        optimized.add(current);

        while (!points.isEmpty()) {
            RoutePoint nearest = findNearestPoint(current, points, coordinates);
            points.remove(nearest);
            optimized.add(nearest);
            current = nearest;
        }

        return optimized;
    }

    private List<RoutePoint> optimizeDayForDistance(RouteDay day) {
        if (day.getRoutePoints() == null || day.getRoutePoints().size() <= 2) {
            return new ArrayList<>(day.getRoutePoints());
        }

        List<RoutePoint> points = new ArrayList<>(day.getRoutePoints());
        Map<Long, double[]> coordinates = getCoordinatesForPoints(points);

        // Для небольшого количества точек используем полный перебор
        if (points.size() <= 8) {
            return bruteForceTSP(points, coordinates);
        } else {
            // Для большего количества используем алгоритм ближайшего соседа
            return nearestNeighborTSP(points, coordinates);
        }
    }

    private List<RoutePoint> bruteForceTSP(List<RoutePoint> points, Map<Long, double[]> coordinates) {
        List<RoutePoint> bestRoute = null;
        double bestDistance = Double.MAX_VALUE;

        // Генерируем все перестановки точек
        int[] indices = new int[points.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        do {
            double totalDistance = 0;
            List<RoutePoint> currentRoute = new ArrayList<>();

            for (int i = 0; i < indices.length; i++) {
                currentRoute.add(points.get(indices[i]));
                if (i > 0) {
                    RoutePoint prev = points.get(indices[i-1]);
                    RoutePoint curr = points.get(indices[i]);
                    totalDistance += distanceService.calculateDistance(
                            coordinates.get(prev.getPoiId()),
                            coordinates.get(curr.getPoiId())
                    );
                }
            }

            if (totalDistance < bestDistance) {
                bestDistance = totalDistance;
                bestRoute = currentRoute;
            }

        } while (nextPermutation(indices));

        return bestRoute != null ? bestRoute : points;
    }

    private List<RoutePoint> nearestNeighborTSP(List<RoutePoint> points, Map<Long, double[]> coordinates) {
        List<RoutePoint> route = new ArrayList<>();
        List<RoutePoint> unvisited = new ArrayList<>(points);

        // Начинаем с произвольной точки
        RoutePoint current = unvisited.remove(0);
        route.add(current);

        while (!unvisited.isEmpty()) {
            RoutePoint nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (RoutePoint point : unvisited) {
                double distance = distanceService.calculateDistance(
                        coordinates.get(current.getPoiId()),
                        coordinates.get(point.getPoiId())
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = point;
                }
            }

            if (nearest != null) {
                unvisited.remove(nearest);
                route.add(nearest);
                current = nearest;
            }
        }

        return route;
    }

    private RoutePoint findNearestPoint(RoutePoint current, List<RoutePoint> candidates,
                                        Map<Long, double[]> coordinates) {
        RoutePoint nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (RoutePoint candidate : candidates) {
            double distance = distanceService.calculateDistance(
                    coordinates.get(current.getPoiId()),
                    coordinates.get(candidate.getPoiId())
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private Map<Long, double[]> getCoordinatesForPoints(List<RoutePoint> points) {
        Map<Long, double[]> coordinates = new HashMap<>();

        for (RoutePoint point : points) {
            try {
                PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                if (poi != null) {
                    coordinates.put(point.getPoiId(),
                            new double[]{poi.getLatitude(), poi.getLongitude()});
                }
            } catch (Exception e) {
                log.warn("Failed to get coordinates for POI {}: {}", point.getPoiId(), e.getMessage());
            }
        }

        return coordinates;
    }

    private int calculateScenicScore(PoiResponse poi) {
        if (poi == null) return 0;

        int score = 0;
        String type = poi.getType() != null ? poi.getType().toLowerCase() : "";

        // Присваиваем баллы за тип объекта
        if (type.contains("park") || type.contains("garden")) score += 10;
        if (type.contains("viewpoint") || type.contains("panorama")) score += 8;
        if (type.contains("museum") || type.contains("gallery")) score += 5;
        if (type.contains("historic") || type.contains("monument")) score += 3;

        // Учитываем рейтинг
        score += (int)(poi.getAverageRating() * 2);

        return score;
    }

    private void updateOrderIndices(RouteDay day) {
        if (day.getRoutePoints() != null) {
            for (int i = 0; i < day.getRoutePoints().size(); i++) {
                day.getRoutePoints().get(i).setOrderIndex((short) (i + 1));
            }
        }
    }

    private void calculateRouteStatistics(Route route) {
        double totalDistance = 0.0;
        int totalDuration = 0;

        for (RouteDay day : route.getRouteDays()) {
            if (day.getRoutePoints() != null && day.getRoutePoints().size() > 1) {
                double dayDistance = calculateDayDistance(day);
                int dayDuration = calculateDayDuration(day);

                totalDistance += dayDistance;
                totalDuration += dayDuration;
            }
        }

        route.setDistanceKm(Math.round(totalDistance * 100.0) / 100.0);
        route.setDurationMin(totalDuration);
    }

    private double calculateDayDistance(RouteDay day) {
        double distance = 0.0;
        Map<Long, double[]> coordinates = new HashMap<>();

        // Получаем координаты всех точек
        for (RoutePoint point : day.getRoutePoints()) {
            try {
                PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                if (poi != null) {
                    coordinates.put(point.getPoiId(),
                            new double[]{poi.getLatitude(), poi.getLongitude()});
                }
            } catch (Exception e) {
                log.warn("Failed to get coordinates for distance calculation: {}", e.getMessage());
            }
        }

        // Суммируем расстояния между последовательными точками
        for (int i = 1; i < day.getRoutePoints().size(); i++) {
            RoutePoint prev = day.getRoutePoints().get(i - 1);
            RoutePoint curr = day.getRoutePoints().get(i);

            double[] coord1 = coordinates.get(prev.getPoiId());
            double[] coord2 = coordinates.get(curr.getPoiId());

            if (coord1 != null && coord2 != null) {
                distance += distanceService.calculateDistance(coord1, coord2);
            }
        }

        return distance;
    }

    private int calculateDayDuration(RouteDay day) {
        int duration = 0;

        for (RoutePoint point : day.getRoutePoints()) {
            duration += point.getEstimatedDuration() != null ? point.getEstimatedDuration() : 60;

            // Добавляем время перемещения между точками (примерно 15 минут между точками)
            duration += 15;
        }

        // Вычитаем лишние 15 минут для последней точки
        return Math.max(0, duration - 15);
    }

    // Вспомогательный метод для генерации перестановок
    private boolean nextPermutation(int[] array) {
        // Находим i
        int i = array.length - 2;
        while (i >= 0 && array[i] >= array[i + 1]) {
            i--;
        }

        if (i < 0) {
            return false;
        }

        // Находим j
        int j = array.length - 1;
        while (array[j] <= array[i]) {
            j--;
        }

        // Меняем местами
        swap(array, i, j);

        // Реверсируем
        reverse(array, i + 1, array.length - 1);
        return true;
    }

    private void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private void reverse(int[] array, int i, int j) {
        while (i < j) {
            swap(array, i, j);
            i++;
            j--;
        }
    }
}