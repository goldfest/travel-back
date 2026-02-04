package com.travelapp.route.service;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import com.travelapp.route.model.dto.response.RouteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RouteService {

    RouteResponse createRoute(Long userId, RouteCreateRequest request);

    RouteResponse getRouteById(Long userId, Long routeId);

    Page<RouteResponse> getUserRoutes(Long userId, Pageable pageable);

    Page<RouteResponse> getArchivedRoutes(Long userId, Pageable pageable);

    RouteResponse updateRoute(Long userId, Long routeId, RouteUpdateRequest request);

    void archiveRoute(Long userId, Long routeId);

    void unarchiveRoute(Long userId, Long routeId);

    void deleteRoute(Long userId, Long routeId);

    RouteResponse duplicateRoute(Long userId, Long routeId, String newName);

    RouteResponse addPoiToRoute(Long userId, Long routeId, Long poiId, Short dayNumber, Short orderIndex);

    RouteResponse removePoiFromRoute(Long userId, Long routeId, Long poiId);

    RouteResponse reorderRoutePoints(Long userId, Long routeId, List<Long> pointIdsInOrder);

    RouteResponse optimizeRoute(Long userId, Long routeId, String optimizationMode);

    List<RouteResponse> getRoutesByCity(Long userId, Long cityId);

    long countUserRoutes(Long userId);

    boolean isRouteNameAvailable(Long userId, String name);
}