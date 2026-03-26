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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutePlanningService {

    private final PoiClient poiClient;
    private final DistanceCalculationService distanceService;

    public PoiResponse findNearestToilet(double latitude, double longitude, int maxDistance, boolean freeOnly, boolean aroundTheClock) {
        List<PoiResponse> toilets = poiClient.searchNearby(latitude, longitude, maxDistance, "toilet");
        return toilets.stream()
                .filter(toilet -> !freeOnly || (toilet.getPriceLevel() != null && toilet.getPriceLevel() == 0))
                .min(Comparator.comparingDouble(t -> distanceService.calculateDistance(new double[]{latitude, longitude}, new double[]{t.getLatitude(), t.getLongitude()})))
                .orElse(null);
    }

    public List<PoiResponse> getRouteSuggestions(Long userId, Long cityId, String poiType, int limit, double minRating) {
        return poiClient.searchByCityAndType(cityId, poiType, 100).stream()
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

    public List<PoiResponse> findAlternativeRoutes(PoiResponse poi, double maxDistanceKm) {
        return poiClient.searchNearby(poi.getLatitude(), poi.getLongitude(), (int) (maxDistanceKm * 1000), poi.getType()).stream()
                .filter(alt -> !alt.getId().equals(poi.getId()))
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
