package com.travelapp.route.service;

import com.travelapp.route.model.dto.routing.RoutingDayResult;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.Route;

import java.util.List;

public interface RoutingProvider {
    RoutingDayResult buildDayRoute(Long cityId, List<RoutingPoint> points, Route.TransportMode transportMode);
    TravelMatrixResult buildMatrix(Long cityId, List<RoutingPoint> points, Route.TransportMode transportMode);
}
