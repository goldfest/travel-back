package com.travelapp.route.controller;

import com.travelapp.route.model.dto.request.*;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.security.SecurityUtils;
import com.travelapp.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/routes")
@RequiredArgsConstructor
@Tag(name = "Route Management", description = "API для управления маршрутами")
public class RouteController {

    private final RouteService routeService;

    @PostMapping
    @Operation(summary = "Создать новый маршрут")
    public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody RouteCreateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(routeService.createRoute(userId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RouteResponse> getRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.getRouteById(userId, id));
    }

    @GetMapping
    public ResponseEntity<Page<RouteResponse>> getUserRoutes(@PageableDefault(size = 20) Pageable pageable) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.getUserRoutes(userId, pageable));
    }

    @GetMapping("/archived")
    public ResponseEntity<Page<RouteResponse>> getArchivedRoutes(@PageableDefault(size = 20) Pageable pageable) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.getArchivedRoutes(userId, pageable));
    }

    @GetMapping("/city/{cityId}")
    public ResponseEntity<List<RouteResponse>> getRoutesByCity(@PathVariable Long cityId) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.getRoutesByCity(userId, cityId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RouteResponse> updateRoute(@PathVariable Long id, @Valid @RequestBody RouteUpdateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.updateRoute(userId, id, request));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archiveRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.archiveRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unarchive")
    public ResponseEntity<Void> unarchiveRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.unarchiveRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.deleteRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<RouteResponse> duplicateRoute(@PathVariable Long id, @RequestParam(required = false) String newName) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.duplicateRoute(userId, id, newName));
    }

    @PostMapping("/{id}/points")
    public ResponseEntity<RouteResponse> addPointToRoute(@PathVariable Long id, @Valid @RequestBody RoutePointRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.addPoiToRoute(userId, id, request.getPoiId(), request.getDayNumber(), request.getOrderIndex()));
    }

    @DeleteMapping("/{routeId}/points/{routePointId}")
    public ResponseEntity<RouteResponse> removePointFromRoute(@PathVariable Long routeId, @PathVariable Long routePointId) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.removePointFromRoute(userId, routeId, routePointId));
    }

    @PostMapping("/{routeId}/days/{dayId}/reorder")
    public ResponseEntity<RouteResponse> reorderDayPoints(@PathVariable Long routeId, @PathVariable Long dayId,
                                                          @Valid @RequestBody ReorderRouteDayPointsRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.reorderRouteDayPoints(userId, routeId, dayId, request.getRoutePointIdsInOrder()));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<RouteResponse> optimizeRoute(
            @PathVariable Long id,
            @RequestBody(required = false) RouteOptimizationRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();

        RouteOptimizationRequest payload = request != null ? request : new RouteOptimizationRequest();
        if (payload.getOptimizationMode() == null || payload.getOptimizationMode().isBlank()) {
            payload.setOptimizationMode("TIME_WINDOW");
        }

        return ResponseEntity.ok(routeService.optimizeRoute(userId, id, payload));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countRoutes() {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.countUserRoutes(userId));
    }

    @GetMapping("/check-name")
    public ResponseEntity<Boolean> checkRouteName(@RequestParam String name) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.isRouteNameAvailable(userId, name));
    }

    @PostMapping("/generate")
    public ResponseEntity<RouteResponse> generateRoute(@Valid @RequestBody RouteGenerateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(routeService.generateRoute(userId, request));
    }
}
