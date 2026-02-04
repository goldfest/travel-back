package com.travelapp.route.controller;

import com.travelapp.route.service.RouteExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Tag(name = "Route Export", description = "API для экспорта маршрутов")
@Slf4j
public class RouteExportController {

    private final RouteExportService routeExportService;

    @GetMapping("/routes/{routeId}/pdf")
    @Operation(summary = "Экспортировать маршрут в PDF")
    public ResponseEntity<byte[]> exportRouteToPdf(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long routeId,
            @Parameter(description = "Включить карту")
            @RequestParam(defaultValue = "true") boolean includeMap,
            @Parameter(description = "Включить детали POI")
            @RequestParam(defaultValue = "true") boolean includePoiDetails) {

        log.info("Exporting route {} to PDF for user {}", routeId, userId);

        byte[] pdfContent = routeExportService.exportRouteToPdf(
                userId, routeId, includeMap, includePoiDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment",
                String.format("route_%d.pdf", routeId));
        headers.setContentLength(pdfContent.length);

        return new ResponseEntity<>(pdfContent, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/routes/{routeId}/gpx")
    @Operation(summary = "Экспортировать маршрут в GPX")
    public ResponseEntity<byte[]> exportRouteToGpx(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long routeId) {

        log.info("Exporting route {} to GPX for user {}", routeId, userId);

        byte[] gpxContent = routeExportService.exportRouteToGpx(userId, routeId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/gpx+xml"));
        headers.setContentDispositionFormData("attachment",
                String.format("route_%d.gpx", routeId));
        headers.setContentLength(gpxContent.length);

        return new ResponseEntity<>(gpxContent, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/routes/{routeId}/json")
    @Operation(summary = "Экспортировать маршрут в JSON")
    public ResponseEntity<byte[]> exportRouteToJson(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long routeId,
            @Parameter(description = "Включить все детали")
            @RequestParam(defaultValue = "true") boolean includeAllDetails) {

        log.info("Exporting route {} to JSON for user {}", routeId, userId);

        byte[] jsonContent = routeExportService.exportRouteToJson(userId, routeId, includeAllDetails);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment",
                String.format("route_%d.json", routeId));
        headers.setContentLength(jsonContent.length);

        return new ResponseEntity<>(jsonContent, headers, org.springframework.http.HttpStatus.OK);
    }

    @GetMapping("/routes/{routeId}/share")
    @Operation(summary = "Получить ссылку для шаринга маршрута")
    public ResponseEntity<String> getShareableLink(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long routeId,
            @Parameter(description = "Срок действия ссылки в днях", example = "7")
            @RequestParam(defaultValue = "7") int expiresInDays) {

        log.info("Generating shareable link for route {} for user {}", routeId, userId);

        String shareLink = routeExportService.generateShareableLink(userId, routeId, expiresInDays);

        return ResponseEntity.ok(shareLink);
    }
}