package com.travelapp.route.service;

import com.travelapp.route.model.entity.Route;

public interface RoutePathCacheService {
    void rebuildRoutePaths(Long routeId);
    void invalidateRoutePaths(Long routeId);
    boolean isRouteCacheMissingOrStale(Route route);
    boolean isRouteCacheActual(Long routeId);
}
