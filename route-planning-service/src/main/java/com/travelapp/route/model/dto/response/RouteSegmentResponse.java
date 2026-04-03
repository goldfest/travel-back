package com.travelapp.route.model.dto.response;

import lombok.Data;

@Data
public class RouteSegmentResponse {
    private Long fromRoutePointId;
    private Long toRoutePointId;
    private Double distanceKm;
    private Integer durationMin;
    private String transportMode;

    private RoutePolylineDto polyline;
    private String provider;
    private String status;
}