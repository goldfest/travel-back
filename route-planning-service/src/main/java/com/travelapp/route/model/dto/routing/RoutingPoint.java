package com.travelapp.route.model.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoutingPoint {
    private Long routePointId;
    private double latitude;
    private double longitude;
}