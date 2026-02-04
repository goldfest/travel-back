package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutePlanningService {

    private final PoiClient poiClient;
    private final DistanceCalculationService distanceService;

    public PoiResponse findNearestToilet(double latitude, double longitude,
                                         int maxDistance, boolean freeOnly,
                                         boolean aroundTheClock) {
        log.debug("Finding nearest toilet at ({}, {}) within {} meters",
                latitude, longitude, maxDistance);

        // Ищем туалеты поблизости
        List<PoiResponse> toilets = poiClient.searchNearby(
                latitude, longitude, maxDistance, "toilet");

        if (toilets.isEmpty()) {
            log.debug("No toilets found within {} meters", maxDistance);
            return null;
        }

        // Фильтруем по критериям
        List<PoiResponse> filteredToilets = toilets.stream()
                .filter(toilet -> !freeOnly || (toilet.getPriceLevel() != null && toilet.getPriceLevel() == 0))
                // Здесь можно добавить фильтрацию по круглосуточности, если есть данные
                .collect(Collectors.toList());

        if (filteredToilets.isEmpty()) {
            log.debug("No toilets matching criteria found");
            return null;
        }

        // Находим ближайший
        double[] currentLocation = {latitude, longitude};
        PoiResponse nearestToilet = null;
        double minDistance = Double.MAX_VALUE;

        for (PoiResponse toilet : filteredToilets) {
            double[] toiletLocation = {toilet.getLatitude(), toilet.getLongitude()};
            double distance = distanceService.calculateDistance(currentLocation, toiletLocation);

            if (distance < minDistance) {
                minDistance = distance;
                nearestToilet = toilet;
            }
        }

        log.info("Found nearest toilet: {} at distance {} km",
                nearestToilet != null ? nearestToilet.getName() : "none",
                String.format("%.2f", minDistance));

        return nearestToilet;
    }

    public List<PoiResponse> getRouteSuggestions(Long userId, Long cityId,
                                                 String poiType, int limit,
                                                 double minRating) {
        log.debug("Getting suggestions for user {} in city {}, type: {}",
                userId, cityId, poiType);

        // Получаем объекты из города с фильтрацией по типу
        List<PoiResponse> allPois = poiClient.searchByCityAndType(cityId, poiType, 100);

        // Фильтруем по рейтингу
        List<PoiResponse> filteredPois = allPois.stream()
                .filter(poi -> poi.getAverageRating() != null && poi.getAverageRating() >= minRating)
                .filter(poi -> poi.getIsVerified() != null && poi.getIsVerified())
                .filter(poi -> poi.getIsClosed() == null || !poi.getIsClosed())
                .limit(limit)
                .collect(Collectors.toList());

        // Сортируем по рейтингу (по убыванию)
        filteredPois.sort((p1, p2) -> {
            double rating1 = p1.getAverageRating() != null ? p1.getAverageRating() : 0.0;
            double rating2 = p2.getAverageRating() != null ? p2.getAverageRating() : 0.0;
            return Double.compare(rating2, rating1);
        });

        log.info("Found {} suggestions for city {}", filteredPois.size(), cityId);
        return filteredPois;
    }

    public Object generateRoute(Long userId, Long cityId, int days,
                                String interests, int budgetLevel,
                                String transportMode) {
        log.info("Generating {}-day route for user {} in city {}", days, userId, cityId);

        // TODO: Реализовать сложную логику генерации маршрута
        // 1. Получение популярных мест по интересам
        // 2. Оптимизация расписания по дням
        // 3. Учет бюджета
        // 4. Балансировка по типам активности

        // Временная заглушка
        return Map.of(
                "status", "under_development",
                "message", "Route generation feature is in development",
                "userId", userId,
                "cityId", cityId,
                "days", days,
                "interests", interests,
                "budgetLevel", budgetLevel,
                "transportMode", transportMode
        );
    }

    public int estimateRouteTime(List<Double> latitudes, List<Double> longitudes,
                                 String transportMode) {
        if (latitudes.size() != longitudes.size() || latitudes.size() < 2) {
            return 0;
        }

        // Рассчитываем общее расстояние
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < latitudes.size(); i++) {
            points.add(new double[]{latitudes.get(i), longitudes.get(i)});
        }

        double totalDistance = distanceService.calculateTotalDistance(points);
        int travelTime = distanceService.calculateTravelTime(totalDistance, transportMode);

        // Добавляем время на посещение каждой точки (примерно 60 минут на точку)
        int visitTime = latitudes.size() * 60;

        return travelTime + visitTime;
    }

    public List<PoiResponse> findAlternativeRoutes(PoiResponse poi, double maxDistanceKm) {
        log.debug("Finding alternative routes near {}", poi.getName());

        List<PoiResponse> alternatives = poiClient.searchNearby(
                poi.getLatitude(), poi.getLongitude(),
                (int)(maxDistanceKm * 1000), // конвертируем в метры
                poi.getType());

        // Фильтруем сам исходный объект
        alternatives = alternatives.stream()
                .filter(alt -> !alt.getId().equals(poi.getId()))
                .filter(alt -> alt.getAverageRating() != null && alt.getAverageRating() >= 4.0)
                .limit(5)
                .collect(Collectors.toList());

        log.info("Found {} alternatives for {}", alternatives.size(), poi.getName());
        return alternatives;
    }

    public boolean validateRouteFeasibility(List<PoiResponse> pois,
                                            int availableHours,
                                            String transportMode) {
        if (pois.size() < 2) {
            return true;
        }

        // Рассчитываем общее время
        int totalTime = estimateRouteTime(
                pois.stream().map(PoiResponse::getLatitude).collect(Collectors.toList()),
                pois.stream().map(PoiResponse::getLongitude).collect(Collectors.toList()),
                transportMode
        );

        // Проверяем, укладывается ли в доступное время
        int availableMinutes = availableHours * 60;
        return totalTime <= availableMinutes;
    }
}