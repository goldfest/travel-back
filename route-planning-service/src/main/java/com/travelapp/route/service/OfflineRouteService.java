package com.travelapp.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.client.PoiClient;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineRouteService {

    private final RouteRepository routeRepository;
    private final PoiClient poiClient;
    private final ObjectMapper objectMapper;

    // In-memory хранилище для оффлайн маршрутов (в реальном приложении используем Redis или БД)
    private final Map<String, byte[]> offlineRouteCache = new HashMap<>();

    public byte[] prepareOfflineRouteData(Long userId, Long routeId,
                                          boolean includePoiDetails,
                                          boolean includeMapData) {
        log.debug("Preparing offline data for route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new RuntimeException("Маршрут не найден"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1. Основная информация о маршруте
            String routeJson = objectMapper.writeValueAsString(convertToOfflineFormat(route));
            addToZip(zos, "route.json", routeJson.getBytes(StandardCharsets.UTF_8));

            // 2. Детали POI, если нужно
            if (includePoiDetails) {
                Map<String, Object> poiDetails = collectPoiDetails(route);
                String poiJson = objectMapper.writeValueAsString(poiDetails);
                addToZip(zos, "poi_details.json", poiJson.getBytes(StandardCharsets.UTF_8));
            }

            // 3. Данные для карты, если нужно
            if (includeMapData) {
                String mapData = generateMapData(route);
                addToZip(zos, "map_data.json", mapData.getBytes(StandardCharsets.UTF_8));
            }

            // 4. Метаданные
            Map<String, Object> metadata = createMetadata(userId, route);
            String metadataJson = objectMapper.writeValueAsString(metadata);
            addToZip(zos, "metadata.json", metadataJson.getBytes(StandardCharsets.UTF_8));

            zos.finish();
            byte[] zipData = baos.toByteArray();

            // Кэшируем оффлайн данные
            String cacheKey = getCacheKey(userId, routeId);
            offlineRouteCache.put(cacheKey, zipData);

            log.info("Offline data prepared for route {}, size: {} bytes", routeId, zipData.length);
            return zipData;

        } catch (IOException e) {
            log.error("Error preparing offline data for route {}", routeId, e);
            throw new RuntimeException("Ошибка при подготовке оффлайн данных", e);
        }
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getOfflineRoutes(Long userId) {
        log.debug("Getting offline routes for user {}", userId);

        List<RouteResponse> offlineRoutes = new ArrayList<>();

        // Ищем маршруты, которые есть в оффлайн кэше
        for (String cacheKey : offlineRouteCache.keySet()) {
            if (cacheKey.startsWith(userId + ":")) {
                Long routeId = extractRouteIdFromCacheKey(cacheKey);

                routeRepository.findByUserIdAndId(userId, routeId).ifPresent(route -> {
                    RouteResponse response = new RouteResponse();
                    response.setId(route.getId());
                    response.setName(route.getName());
                    response.setDescription(route.getDescription());
                    response.setDistanceKm(route.getDistanceKm());
                    response.setDurationMin(route.getDurationMin());
                    response.setCreatedAt(route.getCreatedAt());

                    // Получаем размер оффлайн данных
                    byte[] data = offlineRouteCache.get(cacheKey);
                    if (data != null) {
                        Map<String, Object> offlineInfo = new HashMap<>();
                        offlineInfo.put("sizeBytes", data.length);
                        offlineInfo.put("downloadDate", new Date());
                        response.setAdditionalProperties(offlineInfo);
                    }

                    offlineRoutes.add(response);
                });
            }
        }

        log.info("Found {} offline routes for user {}", offlineRoutes.size(), userId);
        return offlineRoutes;
    }

    public void removeOfflineRoute(Long userId, Long routeId) {
        String cacheKey = getCacheKey(userId, routeId);

        if (offlineRouteCache.containsKey(cacheKey)) {
            byte[] removedData = offlineRouteCache.remove(cacheKey);
            log.info("Removed offline route {} for user {}, freed {} bytes",
                    routeId, userId, removedData != null ? removedData.length : 0);
        } else {
            log.warn("Offline route {} not found for user {}", routeId, userId);
        }
    }

    public Object getStorageUsage(Long userId) {
        long totalSize = 0;
        int routeCount = 0;

        for (Map.Entry<String, byte[]> entry : offlineRouteCache.entrySet()) {
            if (entry.getKey().startsWith(userId + ":")) {
                totalSize += entry.getValue().length;
                routeCount++;
            }
        }

        Map<String, Object> usageInfo = new HashMap<>();
        usageInfo.put("userId", userId);
        usageInfo.put("totalRoutes", routeCount);
        usageInfo.put("totalSizeBytes", totalSize);
        usageInfo.put("totalSizeMB", String.format("%.2f", totalSize / (1024.0 * 1024.0)));
        usageInfo.put("limitBytes", 100 * 1024 * 1024); // 100 MB лимит
        usageInfo.put("usagePercentage", (totalSize * 100.0) / (100 * 1024 * 1024));

        return usageInfo;
    }

    public void syncOfflineData(Long userId) {
        log.info("Syncing offline data for user {}", userId);

        // В реальном приложении здесь была бы синхронизация с сервером
        // Например, загрузка обновленных данных о маршрутах

        // Пока просто логируем
        int syncedCount = 0;

        for (String cacheKey : offlineRouteCache.keySet()) {
            if (cacheKey.startsWith(userId + ":")) {
                syncedCount++;
                Long routeId = extractRouteIdFromCacheKey(cacheKey);
                log.debug("Syncing route {} for user {}", routeId, userId);

                // TODO: Реализовать логику синхронизации
                // 1. Проверить обновления на сервере
                // 2. Обновить оффлайн данные если нужно
                // 3. Уведомить пользователя об изменениях
            }
        }

        log.info("Synced {} offline routes for user {}", syncedCount, userId);
    }

    public Optional<byte[]> getOfflineRouteData(Long userId, Long routeId) {
        String cacheKey = getCacheKey(userId, routeId);
        return Optional.ofNullable(offlineRouteCache.get(cacheKey));
    }

    // Вспомогательные методы

    private Map<String, Object> convertToOfflineFormat(Route route) {
        Map<String, Object> offlineRoute = new HashMap<>();

        offlineRoute.put("id", route.getId());
        offlineRoute.put("name", route.getName());
        offlineRoute.put("description", route.getDescription());
        offlineRoute.put("transportMode", route.getTransportMode());
        offlineRoute.put("distanceKm", route.getDistanceKm());
        offlineRoute.put("durationMin", route.getDurationMin());
        offlineRoute.put("createdAt", route.getCreatedAt());
        offlineRoute.put("updatedAt", route.getUpdatedAt());

        // Дни маршрута
        List<Map<String, Object>> offlineDays = new ArrayList<>();
        for (RouteDay day : route.getRouteDays()) {
            Map<String, Object> offlineDay = new HashMap<>();
            offlineDay.put("dayNumber", day.getDayNumber());
            offlineDay.put("description", day.getDescription());
            offlineDay.put("plannedStart", day.getPlannedStart());
            offlineDay.put("plannedEnd", day.getPlannedEnd());

            // Точки маршрута
            List<Map<String, Object>> offlinePoints = new ArrayList<>();
            for (RoutePoint point : day.getRoutePoints()) {
                Map<String, Object> offlinePoint = new HashMap<>();
                offlinePoint.put("orderIndex", point.getOrderIndex());
                offlinePoint.put("poiId", point.getPoiId());
                offlinePoint.put("estimatedDuration", point.getEstimatedDuration());

                // Базовая информация о POI
                if (point.isPoiDetailsLoaded()) {
                    Map<String, Object> poiInfo = new HashMap<>();
                    poiInfo.put("name", point.getPoiName());
                    poiInfo.put("address", point.getPoiAddress());
                    poiInfo.put("latitude", point.getPoiLatitude());
                    poiInfo.put("longitude", point.getPoiLongitude());
                    poiInfo.put("type", point.getPoiType());
                    offlinePoint.put("poiInfo", poiInfo);
                }

                offlinePoints.add(offlinePoint);
            }
            offlineDay.put("points", offlinePoints);
            offlineDays.add(offlineDay);
        }
        offlineRoute.put("days", offlineDays);

        return offlineRoute;
    }

    private Map<String, Object> collectPoiDetails(Route route) {
        Map<String, Object> poiDetails = new HashMap<>();
        Set<Long> poiIds = new HashSet<>();

        // Собираем все POI ID из маршрута
        for (RouteDay day : route.getRouteDays()) {
            for (RoutePoint point : day.getRoutePoints()) {
                poiIds.add(point.getPoiId());
            }
        }

        // Получаем детальную информацию о каждом POI
        if (!poiIds.isEmpty()) {
            List<PoiResponse> pois = poiClient.getPoisBatch(new ArrayList<>(poiIds));

            Map<Long, Map<String, Object>> detailedPois = new HashMap<>();
            for (PoiResponse poi : pois) {
                Map<String, Object> poiDetail = new HashMap<>();
                poiDetail.put("name", poi.getName());
                poiDetail.put("description", poi.getDescription());
                poiDetail.put("address", poi.getAddress());
                poiDetail.put("latitude", poi.getLatitude());
                poiDetail.put("longitude", poi.getLongitude());
                poiDetail.put("phone", poi.getPhone());
                poiDetail.put("siteUrl", poi.getSiteUrl());
                poiDetail.put("priceLevel", poi.getPriceLevel());
                poiDetail.put("averageRating", poi.getAverageRating());
                poiDetail.put("type", poi.getType());
                poiDetail.put("coverUrl", poi.getCoverUrl());

                detailedPois.put(poi.getId(), poiDetail);
            }

            poiDetails.put("pois", detailedPois);
        }

        return poiDetails;
    }

    private String generateMapData(Route route) {
        // Генерируем упрощенные данные для отображения на карте
        Map<String, Object> mapData = new HashMap<>();
        List<Map<String, Object>> points = new ArrayList<>();

        for (RouteDay day : route.getRouteDays()) {
            for (RoutePoint point : day.getRoutePoints()) {
                Map<String, Object> mapPoint = new HashMap<>();
                mapPoint.put("day", day.getDayNumber());
                mapPoint.put("order", point.getOrderIndex());
                mapPoint.put("poiId", point.getPoiId());

                if (point.isPoiDetailsLoaded()) {
                    mapPoint.put("name", point.getPoiName());
                    mapPoint.put("lat", point.getPoiLatitude());
                    mapPoint.put("lng", point.getPoiLongitude());
                    mapPoint.put("type", point.getPoiType());
                }

                points.add(mapPoint);
            }
        }

        mapData.put("routeId", route.getId());
        mapData.put("routeName", route.getName());
        mapData.put("points", points);
        mapData.put("centerLat", calculateCenterLat(points));
        mapData.put("centerLng", calculateCenterLng(points));
        mapData.put("zoomLevel", calculateZoomLevel(points));

        try {
            return objectMapper.writeValueAsString(mapData);
        } catch (IOException e) {
            log.error("Error generating map data", e);
            return "{}";
        }
    }

    private Map<String, Object> createMetadata(Long userId, Route route) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("version", "1.0");
        metadata.put("format", "travelapp-offline-v1");
        metadata.put("generatedAt", new Date());
        metadata.put("userId", userId);
        metadata.put("routeId", route.getId());
        metadata.put("routeName", route.getName());
        metadata.put("cityId", route.getCityId());
        metadata.put("includesPoiDetails", true);
        metadata.put("includesMapData", true);
        metadata.put("compression", "zip");
        metadata.put("encoding", "UTF-8");

        return metadata;
    }

    private void addToZip(ZipOutputStream zos, String filename, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private String getCacheKey(Long userId, Long routeId) {
        return userId + ":" + routeId;
    }

    private Long extractRouteIdFromCacheKey(String cacheKey) {
        String[] parts = cacheKey.split(":");
        return parts.length > 1 ? Long.parseLong(parts[1]) : null;
    }

    private Double calculateCenterLat(List<Map<String, Object>> points) {
        if (points.isEmpty()) return 0.0;

        double sum = 0.0;
        int count = 0;

        for (Map<String, Object> point : points) {
            Object lat = point.get("lat");
            if (lat instanceof Number) {
                sum += ((Number) lat).doubleValue();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    private Double calculateCenterLng(List<Map<String, Object>> points) {
        if (points.isEmpty()) return 0.0;

        double sum = 0.0;
        int count = 0;

        for (Map<String, Object> point : points) {
            Object lng = point.get("lng");
            if (lng instanceof Number) {
                sum += ((Number) lng).doubleValue();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    private Integer calculateZoomLevel(List<Map<String, Object>> points) {
        if (points.size() < 2) return 12;

        // Простая логика для определения уровня зума
        if (points.size() <= 5) return 13;
        if (points.size() <= 10) return 12;
        if (points.size() <= 20) return 11;
        return 10;
    }
}