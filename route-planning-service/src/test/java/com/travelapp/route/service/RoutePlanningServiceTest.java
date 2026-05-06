package com.travelapp.route.service;

import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutePlanningServiceTest {

    @Mock
    private PoiClient poiClient;

    @Mock
    private DistanceCalculationService distanceService;

    @InjectMocks
    private RoutePlanningService service;

    @Test
    void findNearestToilet_shouldReturnNearestFreeToilet() {
        PoiResponse cafe = poi(1L, "Кафе", "restaurant", 54.31, 48.40, (short) 2, true, false);
        PoiResponse paidToilet = poi(2L, "Платный туалет", "toilet", 54.32, 48.40, (short) 1, true, false);
        PoiResponse freeToilet = poi(3L, "Бесплатный туалет", "toilet", 54.315, 48.405, (short) 0, true, false);

        when(poiClient.searchNearby(10L, 54.3142, 48.4031, 3, 50))
                .thenReturn(List.of(cafe, paidToilet, freeToilet));

        PoiResponse result = service.findNearestToilet(10L, 54.3142, 48.4031, 3, true, false);

        assertThat(result).isEqualTo(freeToilet);
    }

    @Test
    void findNearestToilet_shouldReturnNull_whenNoToiletsFound() {
        when(poiClient.searchNearby(10L, 54.3142, 48.4031, 3, 50))
                .thenReturn(List.of(poi(1L, "Музей", "museum", 54.31, 48.40, (short) 0, true, false)));

        PoiResponse result = service.findNearestToilet(10L, 54.3142, 48.4031, 3, false, false);

        assertThat(result).isNull();
    }

    @Test
    void getRouteSuggestions_shouldFilterByTypeVerifiedAndNotClosed_thenLimit() {
        when(poiClient.searchNearby(10L, 54.3, 48.4, 5, 50)).thenReturn(List.of(
                poi(1L, "Музей 1", "museum", 54.31, 48.41, (short) 0, true, false),
                poi(2L, "Кафе", "restaurant", 54.32, 48.42, (short) 1, true, false),
                poi(3L, "Музей закрыт", "museum", 54.33, 48.43, (short) 0, true, true),
                poi(4L, "Музей 2", "museum", 54.34, 48.44, (short) 0, true, false)
        ));

        List<PoiResponse> result = service.getRouteSuggestions(5L, 10L, 54.3, 48.4, 5, "museum", 1, 0.0);

        assertThat(result).extracting(PoiResponse::getId).containsExactly(1L);
    }

    @Test
    void estimateRouteTime_shouldReturnVisitTimeOnly_whenLessThanTwoPoints() {
        int result = service.estimateRouteTime(List.of(54.3), List.of(48.4), "WALK");

        assertThat(result).isEqualTo(60);
        verifyNoInteractions(distanceService);
    }

    @Test
    void estimateRouteTime_shouldReturnTravelTimePlusVisitTime() {
        when(distanceService.calculateTotalTravelTime(anyList(), eq("WALK"))).thenReturn(30);

        int result = service.estimateRouteTime(
                List.of(54.3, 54.4, 54.5),
                List.of(48.3, 48.4, 48.5),
                "WALK"
        );

        assertThat(result).isEqualTo(210);
    }

    @Test
    void findAlternativeRoutes_shouldReturnSameTypePoisExcludingSource() {
        PoiResponse source = poi(1L, "Исходный музей", "museum", 54.3, 48.4, (short) 0, true, false);
        PoiResponse sameType = poi(2L, "Другой музей", "museum", 54.31, 48.41, (short) 0, true, false);
        PoiResponse otherType = poi(3L, "Кафе", "restaurant", 54.32, 48.42, (short) 1, true, false);

        when(poiClient.searchNearby(10L, 54.3, 48.4, 2, 20))
                .thenReturn(List.of(source, sameType, otherType));

        List<PoiResponse> result = service.findAlternativeRoutes(10L, source, 1.5);

        assertThat(result).containsExactly(sameType);
    }

    @Test
    void generateRoute_shouldReturnUnderDevelopmentPayload() {
        Object result = service.generateRoute(1L, 10L, 2, "музеи", 1, "WALK");

        assertThat(result).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertThat(resultMap).containsEntry("status", "under_development");
    }

    private PoiResponse poi(Long id, String name, String type, Double lat, Double lng, Short priceLevel, Boolean verified, Boolean closed) {
        PoiResponse response = new PoiResponse();
        response.setId(id);
        response.setName(name);
        response.setLatitude(lat);
        response.setLongitude(lng);
        response.setPriceLevel(priceLevel);
        response.setCityId(10L);
        response.setIsVerified(verified);
        response.setIsClosed(closed);
        PoiResponse.PoiTypeDto poiType = new PoiResponse.PoiTypeDto();
        poiType.setCode(type);
        response.setPoiType(poiType);
        return response;
    }
}
