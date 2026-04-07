package com.travelapp.route.controller;

import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.security.SecurityUtils;
import com.travelapp.route.service.OfflineRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/offline")
@RequiredArgsConstructor
@Tag(name = "Offline Routes", description = "API для работы с оффлайн маршрутами")
@Slf4j
public class OfflineController {

    private final OfflineRouteService offlineRouteService;

    @PostMapping("/routes/{routeId}/download")
    @Operation(summary = "Скачать маршрут для оффлайн использования")
    public ResponseEntity<byte[]> downloadRouteForOffline(
            @PathVariable Long routeId,
            @RequestParam(defaultValue = "true") boolean includePoiDetails,
            @RequestParam(defaultValue = "true") boolean includeMapData) {
        Long userId = SecurityUtils.requireUserId();

        log.info("Downloading route {} for offline use by user {}", routeId, userId);

        byte[] offlineData = offlineRouteService.prepareOfflineRouteData(
                userId, routeId, includePoiDetails, includeMapData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment",
                String.format("route_%d_offline.zip", routeId));
        headers.setContentLength(offlineData.length);

        log.info("Route {} prepared for offline use, size: {} bytes",
                routeId, offlineData.length);

        return new ResponseEntity<>(offlineData, headers, HttpStatus.OK);
    }

    @GetMapping("/routes")
    @Operation(summary = "Получить список оффлайн маршрутов")
    public ResponseEntity<List<RouteResponse>> getOfflineRoutes() {
        Long userId = SecurityUtils.requireUserId();

        log.info("Getting offline routes for user {}", userId);

        List<RouteResponse> offlineRoutes = offlineRouteService.getOfflineRoutes(userId);

        return ResponseEntity.ok(offlineRoutes);
    }

    @DeleteMapping("/routes/{routeId}")
    @Operation(summary = "Удалить маршрут из оффлайн хранилища")
    public ResponseEntity<Void> removeOfflineRoute(
            @PathVariable Long routeId) {
        Long userId = SecurityUtils.requireUserId();


        log.info("Removing route {} from offline storage for user {}", routeId, userId);

        offlineRouteService.removeOfflineRoute(userId, routeId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storage/usage")
    @Operation(summary = "Получить информацию об использовании оффлайн хранилища")
    public ResponseEntity<Object> getStorageUsage() {
        Long userId = SecurityUtils.requireUserId();

        log.info("Getting storage usage for user {}", userId);

        Object usageInfo = offlineRouteService.getStorageUsage(userId);

        return ResponseEntity.ok(usageInfo);
    }

    @PostMapping("/sync")
    @Operation(summary = "Синхронизировать оффлайн данные")
    public ResponseEntity<Void> syncOfflineData() {
        Long userId = SecurityUtils.requireUserId();

        log.info("Syncing offline data for user {}", userId);

        offlineRouteService.syncOfflineData(userId);

        return ResponseEntity.noContent().build();
    }
}