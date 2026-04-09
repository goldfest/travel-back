package com.travelapp.route.service;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteGenerateRequest;
import com.travelapp.route.model.dto.request.RouteOptimizationRequest;
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

    List<RouteResponse> getRoutesByCity(Long userId, Long cityId);

    RouteResponse updateRoute(Long userId, Long routeId, RouteUpdateRequest request);

    void archiveRoute(Long userId, Long routeId);

    void unarchiveRoute(Long userId, Long routeId);

    void deleteRoute(Long userId, Long routeId);

    RouteResponse duplicateRoute(Long userId, Long routeId, String newName);

    RouteResponse addPoiToRoute(Long userId, Long routeId, Long poiId, Short dayNumber, Short orderIndex);

    RouteResponse removePointFromRoute(Long userId, Long routeId, Long routePointId);

    RouteResponse reorderRouteDayPoints(Long userId, Long routeId, Long dayId, List<Long> pointIdsInOrder);

    RouteResponse optimizeRoute(Long userId, Long routeId, RouteOptimizationRequest request);

    RouteResponse generateRoute(Long userId, RouteGenerateRequest request);

    long countUserRoutes(Long userId);

    boolean isRouteNameAvailable(Long userId, String name);
}
