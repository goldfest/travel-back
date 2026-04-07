package com.travelapp.route.model.dto.response;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RouteMapResponse {
    private Long routeId;
    private String routeName;
    private String description;
    private String transportMode;

    private BigDecimal totalDistanceKm;
    private Integer totalDurationMin;

    private MapViewportDto viewport;
    private List<RouteMapDayResponse> days;
}