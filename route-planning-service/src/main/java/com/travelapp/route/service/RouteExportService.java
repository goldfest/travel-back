package com.travelapp.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteExportService {

    private final RouteRepository routeRepository;
    private final PoiClient poiClient;
    private final ObjectMapper objectMapper;

    // Хранилище шаринговых ссылок (в продакшене используем Redis)
    private final Map<String, ShareableRoute> shareableRoutes = new ConcurrentHashMap<>();

    public byte[] exportRouteToPdf(Long userId, Long routeId, boolean includeMap, boolean includePoiDetails) {
        log.debug("Exporting route {} to PDF for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new RuntimeException("Маршрут не найден"));

        try {
            // Генерируем простой PDF (в реальном приложении используем библиотеку типа iText)
            String pdfContent = generatePdfContent(route, includeMap, includePoiDetails);
            return pdfContent.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error generating PDF for route {}", routeId, e);
            throw new RuntimeException("Ошибка при генерации PDF", e);
        }
    }

    public byte[] exportRouteToGpx(Long userId, Long routeId) {
        log.debug("Exporting route {} to GPX for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new RuntimeException("Маршрут не найден"));

        try {
            String gpxContent = generateGpxContent(route);
            return gpxContent.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Error generating GPX for route {}", routeId, e);
            throw new RuntimeException("Ошибка при генерации GPX", e);
        }
    }

    public byte[] exportRouteToJson(Long userId, Long routeId, boolean includeAllDetails) {
        log.debug("Exporting route {} to JSON for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new RuntimeException("Маршрут не найден"));

        try {
            Map<String, Object> exportData = convertToExportFormat(route, includeAllDetails);
            return objectMapper.writeValueAsBytes(exportData);

        } catch (IOException e) {
            log.error("Error generating JSON for route {}", routeId, e);
            throw new RuntimeException("Ошибка при генерации JSON", e);
        }
    }

    public String generateShareableLink(Long userId, Long routeId, int expiresInDays) {
        log.debug("Generating shareable link for route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new RuntimeException("Маршрут не найден"));

        // Генерируем уникальный токен
        String token = UUID.randomUUID().toString();

        // Сохраняем информацию о шаринге
        ShareableRoute shareableRoute = new ShareableRoute();
        shareableRoute.setRouteId(routeId);
        shareableRoute.setUserId(userId);
        shareableRoute.setCreatedAt(LocalDateTime.now());
        shareableRoute.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        shareableRoute.setViewCount(0);

        shareableRoutes.put(token, shareableRoute);

        // Генерируем ссылку
        String baseUrl = "https://travelapp.com/share/route";
        return String.format("%s/%s", baseUrl, token);
    }

    public Optional<Route> getSharedRoute(String token) {
        ShareableRoute shareableRoute = shareableRoutes.get(token);

        if (shareableRoute == null) {
            return Optional.empty();
        }

        // Проверяем срок действия
        if (shareableRoute.getExpiresAt().isBefore(LocalDateTime.now())) {
            shareableRoutes.remove(token);
            return Optional.empty();
        }

        // Увеличиваем счетчик просмотров
        shareableRoute.setViewCount(shareableRoute.getViewCount() + 1);

        return routeRepository.findById(shareableRoute.getRouteId());
    }

    // Вспомогательные методы

    private String generatePdfContent(Route route, boolean includeMap, boolean includePoiDetails) {
        StringBuilder pdf = new StringBuilder();

        // Простой PDF-подобный формат (в реальном приложении используем настоящий PDF)
        pdf.append("%PDF-1.4\n");
        pdf.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        pdf.append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");
        pdf.append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R >>\nendobj\n");
        pdf.append("4 0 obj\n<< /Length 1000 >>\nstream\n");

        // Контент страницы
        pdf.append("BT\n/F1 12 Tf\n72 720 Td\n(").append(route.getName()).append(") Tj\nET\n");
        pdf.append("BT\n/F1 10 Tf\n72 700 Td\n(").append(route.getDescription()).append(") Tj\nET\n");

        if (includePoiDetails && route.getRouteDays() != null) {
            int yPos = 680;
            for (RouteDay day : route.getRouteDays()) {
                pdf.append("BT\n/F1 11 Tf\n72 ").append(yPos).append(" Td\n(День ").append(day.getDayNumber()).append(") Tj\nET\n");
                yPos -= 15;

                if (day.getRoutePoints() != null) {
                    for (RoutePoint point : day.getRoutePoints()) {
                        try {
                            PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                            if (poi != null) {
                                pdf.append("BT\n/F1 9 Tf\n85 ").append(yPos).append(" Td\n(")
                                        .append(point.getOrderIndex()).append(". ")
                                        .append(poi.getName()).append(") Tj\nET\n");
                                yPos -= 12;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get POI details for PDF export", e);
                        }
                    }
                }
                yPos -= 10;
            }
        }

        pdf.append("endstream\nendobj\n");
        pdf.append("xref\n0 5\n0000000000 65535 f \n");
        pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\n");
        pdf.append("startxref\n1000\n%%EOF\n");

        return pdf.toString();
    }

    private String generateGpxContent(Route route) {
        StringBuilder gpx = new StringBuilder();

        gpx.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        gpx.append("<gpx version=\"1.1\" creator=\"TravelApp\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        gpx.append("  <metadata>\n");
        gpx.append("    <name>").append(escapeXml(route.getName())).append("</name>\n");
        gpx.append("    <desc>").append(escapeXml(route.getDescription())).append("</desc>\n");
        gpx.append("    <time>").append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("</time>\n");
        gpx.append("  </metadata>\n");

        if (route.getRouteDays() != null) {
            int trackNumber = 1;
            for (RouteDay day : route.getRouteDays()) {
                gpx.append("  <trk>\n");
                gpx.append("    <name>День ").append(day.getDayNumber()).append("</name>\n");
                gpx.append("    <trkseg>\n");

                if (day.getRoutePoints() != null) {
                    for (RoutePoint point : day.getRoutePoints()) {
                        try {
                            PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                            if (poi != null) {
                                gpx.append("      <trkpt lat=\"").append(poi.getLatitude())
                                        .append("\" lon=\"").append(poi.getLongitude()).append("\">\n");
                                gpx.append("        <name>").append(escapeXml(poi.getName())).append("</name>\n");
                                gpx.append("        <desc>").append(escapeXml(poi.getAddress())).append("</desc>\n");
                                gpx.append("        <sym>Flag</sym>\n");
                                gpx.append("      </trkpt>\n");
                            }
                        } catch (Exception e) {
                            log.warn("Failed to get POI details for GPX export", e);
                        }
                    }
                }

                gpx.append("    </trkseg>\n");
                gpx.append("  </trk>\n");
                trackNumber++;
            }
        }

        gpx.append("</gpx>");

        return gpx.toString();
    }

    private Map<String, Object> convertToExportFormat(Route route, boolean includeAllDetails) {
        Map<String, Object> exportData = new LinkedHashMap<>();

        exportData.put("id", route.getId());
        exportData.put("name", route.getName());
        exportData.put("description", route.getDescription());
        exportData.put("transportMode", route.getTransportMode());
        exportData.put("distanceKm", route.getDistanceKm());
        exportData.put("durationMin", route.getDurationMin());
        exportData.put("createdAt", route.getCreatedAt());
        exportData.put("updatedAt", route.getUpdatedAt());
        exportData.put("exportedAt", LocalDateTime.now());

        if (includeAllDetails && route.getRouteDays() != null) {
            List<Map<String, Object>> daysData = new ArrayList<>();

            for (RouteDay day : route.getRouteDays()) {
                Map<String, Object> dayData = new LinkedHashMap<>();
                dayData.put("dayNumber", day.getDayNumber());
                dayData.put("description", day.getDescription());
                dayData.put("plannedStart", day.getPlannedStart());
                dayData.put("plannedEnd", day.getPlannedEnd());

                if (day.getRoutePoints() != null) {
                    List<Map<String, Object>> pointsData = new ArrayList<>();

                    for (RoutePoint point : day.getRoutePoints()) {
                        Map<String, Object> pointData = new LinkedHashMap<>();
                        pointData.put("orderIndex", point.getOrderIndex());
                        pointData.put("poiId", point.getPoiId());
                        pointData.put("estimatedDuration", point.getEstimatedDuration());

                        if (includeAllDetails) {
                            try {
                                PoiResponse poi = poiClient.getPoiById(point.getPoiId()).orElse(null);
                                if (poi != null) {
                                    Map<String, Object> poiData = new LinkedHashMap<>();
                                    poiData.put("name", poi.getName());
                                    poiData.put("description", poi.getDescription());
                                    poiData.put("address", poi.getAddress());
                                    poiData.put("latitude", poi.getLatitude());
                                    poiData.put("longitude", poi.getLongitude());
                                    poiData.put("phone", poi.getPhone());
                                    poiData.put("siteUrl", poi.getSiteUrl());
                                    poiData.put("priceLevel", poi.getPriceLevel());
                                    poiData.put("averageRating", poi.getAverageRating());
                                    poiData.put("type", poi.getType());

                                    pointData.put("poiDetails", poiData);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to get POI details for JSON export", e);
                            }
                        }

                        pointsData.add(pointData);
                    }

                    dayData.put("points", pointsData);
                }

                daysData.add(dayData);
            }

            exportData.put("days", daysData);
        }

        return exportData;
    }

    private String escapeXml(String input) {
        if (input == null) return "";

        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    // Внутренний класс для хранения информации о шаринговых маршрутах
    private static class ShareableRoute {
        private Long routeId;
        private Long userId;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private int viewCount;

        // Getters and setters
        public Long getRouteId() { return routeId; }
        public void setRouteId(Long routeId) { this.routeId = routeId; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

        public int getViewCount() { return viewCount; }
        public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    }
}