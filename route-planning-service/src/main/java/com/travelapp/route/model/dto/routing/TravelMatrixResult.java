package com.travelapp.route.model.dto.routing;

import lombok.Data;

@Data
public class TravelMatrixResult {
    private double[][] distanceKm;
    private int[][] durationMin;
}