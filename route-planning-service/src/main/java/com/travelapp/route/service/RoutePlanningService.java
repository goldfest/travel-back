package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutePlanningService {

    private final PoiClient poiClient;
    private final DistanceCalculationService distanceService;

    private String extractPoiType(PoiResponse poi) {
        return poi != null && poi.getPoiType() != null ? poi.getPoiType().getCode() : null;
    }

    public PoiResponse findNearestToilet(Long cityId,
                                         double latitude,
                                         double longitude,
                                         int radiusKm,
                                         boolean freeOnly,
                                         boolean aroundTheClock) {
        List<PoiResponse> toilets = poiClient.searchNearby(cityId, latitude, longitude, radiusKm, 50);

        return toilets.stream()
                .filter(Objects::nonNull)
                .filter(poi -> "toilet".equalsIgnoreCase(extractPoiType(poi)))
                .filter(toilet -> !freeOnly || (toilet.getPriceLevel() != null && toilet.getPriceLevel() == 0))
                .min(Comparator.comparingDouble(t ->
                        distanceService.calculateDistance(
                                new double[]{latitude, longitude},
                                new double[]{t.getLatitude(), t.getLongitude()}
                        )))
                .orElse(null);
    }

    public List<PoiResponse> getRouteSuggestions(Long userId,
                                                 Long cityId,
                                                 double latitude,
                                                 double longitude,
                                                 int radiusKm,
                                                 String poiType,
                                                 int limit,
                                                 double minRating) {
        return poiClient.searchNearby(cityId, latitude, longitude, radiusKm, Math.max(limit * 3, 50)).stream()
                .filter(Objects::nonNull)
                .filter(poi -> poiType == null || poiType.isBlank()
                        || poiType.equalsIgnoreCase(extractPoiType(poi)))
                .filter(poi -> poi.getIsVerified() == null || poi.getIsVerified())
                .filter(poi -> poi.getIsClosed() == null || !poi.getIsClosed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Object generateRoute(Long userId, Long cityId, int days, String interests, int budgetLevel, String transportMode) {
        return Map.of(
                "status", "under_development",
                "message", "Use POST /v1/routes/generate in RouteService for persisted route generation",
                "userId", userId,
                "cityId", cityId,
                "days", days,
                "interests", interests,
                "budgetLevel", budgetLevel,
                "transportMode", transportMode
        );
    }

    public int estimateRouteTime(List<Double> latitudes, List<Double> longitudes, String transportMode) {
        if (latitudes.size() != longitudes.size() || latitudes.size() < 2) {
            return latitudes.size() * 60;
        }
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < latitudes.size(); i++) {
            points.add(new double[]{latitudes.get(i), longitudes.get(i)});
        }
        return distanceService.calculateTotalTravelTime(points, transportMode) + latitudes.size() * 60;
    }

    public List<PoiResponse> findAlternativeRoutes(Long cityId, PoiResponse poi, double maxDistanceKm) {
        if (poi == null || poi.getLatitude() == null || poi.getLongitude() == null) {
            return List.of();
        }

        String sourceType = extractPoiType(poi);

        return poiClient.searchNearby(
                        cityId,
                        poi.getLatitude(),
                        poi.getLongitude(),
                        (int) Math.ceil(maxDistanceKm),
                        20
                ).stream()
                .filter(Objects::nonNull)
                .filter(alt -> !alt.getId().equals(poi.getId()))
                .filter(alt -> sourceType == null || sourceType.equalsIgnoreCase(extractPoiType(alt)))
                .limit(5)
                .collect(Collectors.toList());
    }

    public boolean validateRouteFeasibility(List<PoiResponse> pois, int availableHours, String transportMode) {
        if (pois.size() < 2) {
            return true;
        }
        int totalTime = estimateRouteTime(
                pois.stream().map(PoiResponse::getLatitude).collect(Collectors.toList()),
                pois.stream().map(PoiResponse::getLongitude).collect(Collectors.toList()),
                transportMode
        );
        return totalTime <= availableHours * 60;
    }
}