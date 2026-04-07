package com.travelapp.route.service;

import com.travelapp.route.model.dto.response.RouteMapResponse;

public interface RouteMapService {
    RouteMapResponse getRouteMap(Long userId, Long routeId);
}