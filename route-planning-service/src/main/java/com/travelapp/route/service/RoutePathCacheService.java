package com.travelapp.route.service;

public interface RoutePathCacheService {
    void rebuildRoutePaths(Long routeId);
    void invalidateRoutePaths(Long routeId);
}