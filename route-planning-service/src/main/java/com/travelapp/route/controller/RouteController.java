package com.travelapp.route.controller;

import com.travelapp.route.model.dto.request.RouteCreateRequest;
import com.travelapp.route.model.dto.request.RouteOptimizationRequest;
import com.travelapp.route.model.dto.request.RoutePointRequest;
import com.travelapp.route.model.dto.request.RouteUpdateRequest;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.security.SecurityUtils;
import com.travelapp.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    public ResponseEntity<RouteResponse> createRoute(
            @Valid @RequestBody RouteCreateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.createRoute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить маршрут по ID")
    public ResponseEntity<RouteResponse> getRoute(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.getRouteById(userId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Получить все маршруты пользователя")
    public ResponseEntity<Page<RouteResponse>> getUserRoutes(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<RouteResponse> routes = routeService.getUserRoutes(userId, pageable);
        return ResponseEntity.ok(routes);
    }

    @GetMapping("/archived")
    @Operation(summary = "Получить архивные маршруты")
    public ResponseEntity<Page<RouteResponse>> getArchivedRoutes(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<RouteResponse> routes = routeService.getArchivedRoutes(userId, pageable);
        return ResponseEntity.ok(routes);
    }

    @GetMapping("/city/{cityId}")
    @Operation(summary = "Получить маршруты по городу")
    public ResponseEntity<List<RouteResponse>> getRoutesByCity(
            @PathVariable Long cityId) {
        Long userId = SecurityUtils.requireUserId();
        List<RouteResponse> routes = routeService.getRoutesByCity(userId, cityId);
        return ResponseEntity.ok(routes);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить маршрут")
    public ResponseEntity<RouteResponse> updateRoute(
            @PathVariable Long id,
            @Valid @RequestBody RouteUpdateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.updateRoute(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Архивировать маршрут")
    public ResponseEntity<Void> archiveRoute(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.archiveRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Разархивировать маршрут")
    public ResponseEntity<Void> unarchiveRoute(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.unarchiveRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить маршрут")
    public ResponseEntity<Void> deleteRoute(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        routeService.deleteRoute(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Дублировать маршрут")
    public ResponseEntity<RouteResponse> duplicateRoute(
            @PathVariable Long id,
            @RequestParam(required = false) String newName) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.duplicateRoute(userId, id, newName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/points")
    @Operation(summary = "Добавить точку в маршрут")
    public ResponseEntity<RouteResponse> addPointToRoute(
            @PathVariable Long id,
            @Valid @RequestBody RoutePointRequest request) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.addPoiToRoute(
                userId, id, request.getPoiId(), request.getDayNumber(), request.getOrderIndex());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/points/{poiId}")
    @Operation(summary = "Удалить точку из маршрута")
    public ResponseEntity<RouteResponse> removePointFromRoute(
            @PathVariable Long id,
            @PathVariable Long poiId) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.removePoiFromRoute(userId, id, poiId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reorder")
    @Operation(summary = "Изменить порядок точек в маршруте")
    public ResponseEntity<RouteResponse> reorderPoints(
            @PathVariable Long id,
            @RequestBody List<Long> pointIdsInOrder) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.reorderRoutePoints(userId, id, pointIdsInOrder);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/optimize")
    @Operation(summary = "Оптимизировать маршрут")
    public ResponseEntity<RouteResponse> optimizeRoute(
            @PathVariable Long id,
            @Valid @RequestBody RouteOptimizationRequest request) {
        Long userId = SecurityUtils.requireUserId();
        RouteResponse response = routeService.optimizeRoute(userId, id, request.getOptimizationMode());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    @Operation(summary = "Получить количество активных маршрутов")
    public ResponseEntity<Long> countRoutes() {
        Long userId = SecurityUtils.requireUserId();
        long count = routeService.countUserRoutes(userId);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/check-name")
    @Operation(summary = "Проверить доступность имени маршрута")
    public ResponseEntity<Boolean> checkRouteName(
            @RequestParam String name) {
        Long userId = SecurityUtils.requireUserId();
        boolean available = routeService.isRouteNameAvailable(userId, name);
        return ResponseEntity.ok(available);
    }
}