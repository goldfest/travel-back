package com.travelapp.route.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceCalculationServiceTest {

    private final DistanceCalculationService service = new DistanceCalculationService();

    @Test
    void calculateDistance_shouldReturnZero_whenPointIsInvalid() {
        assertThat(service.calculateDistance(null, new double[]{54.3, 48.4})).isZero();
        assertThat(service.calculateDistance(new double[]{54.3}, new double[]{54.3, 48.4})).isZero();
    }

    @Test
    void calculateDistance_shouldReturnApproximateDistanceBetweenUlyanovskPoints() {
        double result = service.calculateDistance(
                new double[]{54.3142, 48.4031},
                new double[]{54.3180, 48.3970}
        );

        assertThat(result).isBetween(0.4, 0.8);
    }

    @Test
    void calculateTravelTime_shouldUseExpectedTransportSpeeds() {
        assertThat(service.calculateTravelTime(10.0, "WALK")).isEqualTo(120);
        assertThat(service.calculateTravelTime(10.0, "CAR")).isEqualTo(15);
        assertThat(service.calculateTravelTime(10.0, "PUBLIC_TRANSPORT")).isEqualTo(24);
        assertThat(service.calculateTravelTime(10.0, "MIXED")).isEqualTo(40);
        assertThat(service.calculateTravelTime(10.0, "UNKNOWN")).isEqualTo(120);
    }

    @Test
    void findNearestPoint_shouldReturnNearestIndexAndRoundedDistance() {
        DistanceCalculationService.NearestPoint result = service.findNearestPoint(
                new double[]{54.3142, 48.4031},
                List.of(
                        new double[]{54.4000, 48.5000},
                        new double[]{54.3145, 48.4035},
                        new double[]{55.0000, 49.0000}
                )
        );

        assertThat(result).isNotNull();
        assertThat(result.getIndex()).isEqualTo(1);
        assertThat(result.getDistanceKm()).isLessThan(0.1);
    }

    @Test
    void calculateTotalDistance_shouldSumSegments() {
        double result = service.calculateTotalDistance(List.of(
                new double[]{54.3142, 48.4031},
                new double[]{54.3180, 48.3970},
                new double[]{54.3210, 48.3900}
        ));

        assertThat(result).isGreaterThan(0.8);
    }

    @Test
    void isWithinRadius_shouldReturnTrueOnlyForPointsInsideRadius() {
        double[] center = {54.3142, 48.4031};
        double[] near = {54.3145, 48.4035};
        double[] far = {55.0000, 49.0000};

        assertThat(service.isWithinRadius(center, near, 1.0)).isTrue();
        assertThat(service.isWithinRadius(center, far, 1.0)).isFalse();
    }
}
