package com.travelapp.route.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.client.PoiClient;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.dto.offline.OfflineRouteMeta;
import com.travelapp.route.model.dto.response.PoiResponse;
import com.travelapp.route.model.dto.response.RouteResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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

    // RedisTemplates
    private final RedisTemplate<String, byte[]> offlineBinaryRedisTemplate;
    private final RedisTemplate<String, String> offlineStringRedisTemplate;

    @Value("${app.offline.ttl-days:30}")
    private int ttlDays;

    @Value("${app.offline.key-prefix:offline}")
    private String keyPrefix;

    @Value("${app.offline.storage-limit-bytes:104857600}")
    private long storageLimitBytes; // 100 MB по умолчанию

    public byte[] prepareOfflineRouteData(Long userId, Long routeId, boolean includePoiDetails, boolean includeMapData) {
        log.debug("Preparing offline data for route {} for user {}", routeId, userId);

        Route route = routeRepository.findByUserIdAndId(userId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        // 1) Собираем POI batch’ем (чтобы не было N+1 и чтобы offline был реально с POI)
        Map<Long, PoiResponse> poiMap = loadPoisAsMap(route);

        // 2) Собираем ZIP
        byte[] zipData = buildZip(route, poiMap, includePoiDetails, includeMapData, userId);

        // 3) Сохраняем в Redis + TTL
        Duration ttl = Duration.ofDays(ttlDays);

        String zipKey = routeZipKey(userId, routeId);
        String metaKey = routeMetaKey(userId, routeId);

        OfflineRouteMeta meta = OfflineRouteMeta.builder()
                .userId(userId)
                .routeId(routeId)
                .routeName(route.getName())
                .cityId(route.getCityId())
                .includesPoiDetails(includePoiDetails)
                .includesMapData(includeMapData)
                .sizeBytes(zipData.length)
                .downloadedAt(Instant.now())
                .format("travelapp-offline-v1")
                .version("1.0")
                .build();

        // лимит хранилища (простая проверка перед записью)
        long usage = getUserStorageUsageBytes(userId);
        if (usage + zipData.length > storageLimitBytes) {
            throw new IllegalStateException("Превышен лимит оффлайн хранилища");
        }

        offlineBinaryRedisTemplate.opsForValue().set(zipKey, zipData, ttl);
        offlineStringRedisTemplate.opsForValue().set(metaKey, toJson(meta), ttl);

        log.info("Offline data prepared route {}, size={} bytes, ttl={} days", routeId, zipData.length, ttlDays);
        return zipData;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> getOfflineRoutes(Long userId) {
        log.debug("Getting offline routes for user {}", userId);

        // Ищем все meta ключи пользователя
        String pattern = routeMetaKey(userId, "*");
        List<String> metaKeys = scanKeys(pattern);

        List<RouteResponse> result = new ArrayList<>();
        for (String metaKey : metaKeys) {
            String metaJson = offlineStringRedisTemplate.opsForValue().get(metaKey);
            if (metaJson == null) continue;

            OfflineRouteMeta meta;
            try {
                meta = objectMapper.readValue(metaJson, OfflineRouteMeta.class);
            } catch (Exception e) {
                log.warn("Bad offline meta json for key={}", metaKey, e);
                continue;
            }

            // Подтягиваем основной route из БД (если надо)
            routeRepository.findByUserIdAndId(userId, meta.getRouteId()).ifPresent(route -> {
                RouteResponse response = new RouteResponse();
                response.setId(route.getId());
                response.setName(route.getName());
                response.setDescription(route.getDescription());
                response.setDistanceKm(route.getDistanceKm());
                response.setDurationMin(route.getDurationMin());
                response.setCreatedAt(route.getCreatedAt());

                Map<String, Object> offlineInfo = new HashMap<>();
                offlineInfo.put("sizeBytes", meta.getSizeBytes());
                offlineInfo.put("downloadedAt", meta.getDownloadedAt());
                offlineInfo.put("includesPoiDetails", meta.isIncludesPoiDetails());
                offlineInfo.put("includesMapData", meta.isIncludesMapData());
                offlineInfo.put("format", meta.getFormat());
                offlineInfo.put("version", meta.getVersion());

                response.setAdditionalProperties(offlineInfo);
                result.add(response);
            });
        }

        log.info("Found {} offline routes for user {}", result.size(), userId);
        return result;
    }

    public void removeOfflineRoute(Long userId, Long routeId) {
        String zipKey = routeZipKey(userId, routeId);
        String metaKey = routeMetaKey(userId, routeId);

        Boolean z = offlineBinaryRedisTemplate.delete(zipKey);
        Boolean m = offlineStringRedisTemplate.delete(metaKey);

        log.info("Removed offline route {} for user {}, zipDeleted={}, metaDeleted={}", routeId, userId, z, m);
    }

    public Map<String, Object> getStorageUsage(Long userId) {
        long totalSize = getUserStorageUsageBytes(userId);
        int routeCount = countUserOfflineRoutes(userId);

        Map<String, Object> usageInfo = new HashMap<>();
        usageInfo.put("userId", userId);
        usageInfo.put("totalRoutes", routeCount);
        usageInfo.put("totalSizeBytes", totalSize);
        usageInfo.put("totalSizeMB", String.format("%.2f", totalSize / (1024.0 * 1024.0)));
        usageInfo.put("limitBytes", storageLimitBytes);
        usageInfo.put("usagePercentage", storageLimitBytes == 0 ? 0 : (totalSize * 100.0) / storageLimitBytes);
        usageInfo.put("ttlDays", ttlDays);
        return usageInfo;
    }

    public void syncOfflineData(Long userId) {
        // В дипломе можно оставить stub, но корректно.
        // Здесь стоит: пересобрать zip если route.updatedAt > meta.downloadedAt, но это требует доп. логики.
        log.info("Sync offline data requested for user {}", userId);
    }

    public Optional<byte[]> getOfflineRouteData(Long userId, Long routeId) {
        String zipKey = routeZipKey(userId, routeId);
        return Optional.ofNullable(offlineBinaryRedisTemplate.opsForValue().get(zipKey));
    }

    // ------------------------
    // Вспомогательные методы
    // ------------------------

    private byte[] buildZip(Route route,
                            Map<Long, PoiResponse> poiMap,
                            boolean includePoiDetails,
                            boolean includeMapData,
                            Long userId) {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            // 1) route.json
            String routeJson = objectMapper.writeValueAsString(convertToOfflineFormat(route, poiMap));
            addToZip(zos, "route.json", routeJson.getBytes(StandardCharsets.UTF_8));

            // 2) poi_details.json
            if (includePoiDetails) {
                Map<String, Object> poiDetails = convertPoiDetails(poiMap);
                String poiJson = objectMapper.writeValueAsString(poiDetails);
                addToZip(zos, "poi_details.json", poiJson.getBytes(StandardCharsets.UTF_8));
            }

            // 3) map_data.json
            if (includeMapData) {
                String mapJson = generateMapData(route, poiMap);
                addToZip(zos, "map_data.json", mapJson.getBytes(StandardCharsets.UTF_8));
            }

            // 4) metadata.json (внутрь архива тоже кладём)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("version", "1.0");
            metadata.put("format", "travelapp-offline-v1");
            metadata.put("generatedAt", Instant.now().toString());
            metadata.put("userId", userId);
            metadata.put("routeId", route.getId());
            metadata.put("routeName", route.getName());
            metadata.put("cityId", route.getCityId());
            metadata.put("includesPoiDetails", includePoiDetails);
            metadata.put("includesMapData", includeMapData);
            metadata.put("compression", "zip");
            metadata.put("encoding", "UTF-8");

            addToZip(zos, "metadata.json", objectMapper.writeValueAsBytes(metadata));

            zos.finish();
            return baos.toByteArray();

        } catch (IOException e) {
            log.error("Error preparing offline zip for route {}", route.getId(), e);
            throw new RuntimeException("Ошибка при подготовке оффлайн данных", e);
        }
    }

    private Map<Long, PoiResponse> loadPoisAsMap(Route route) {
        Set<Long> poiIds = new HashSet<>();
        if (route.getRouteDays() != null) {
            for (RouteDay day : route.getRouteDays()) {
                if (day.getRoutePoints() != null) {
                    for (RoutePoint p : day.getRoutePoints()) {
                        poiIds.add(p.getPoiId());
                    }
                }
            }
        }

        if (poiIds.isEmpty()) return Map.of();

        List<PoiResponse> pois = poiClient.getPoisBatch(new ArrayList<>(poiIds));
        Map<Long, PoiResponse> map = new HashMap<>();
        for (PoiResponse p : pois) map.put(p.getId(), p);
        return map;
    }

    private Map<String, Object> convertToOfflineFormat(Route route, Map<Long, PoiResponse> poiMap) {
        Map<String, Object> offlineRoute = new LinkedHashMap<>();
        offlineRoute.put("id", route.getId());
        offlineRoute.put("name", route.getName());
        offlineRoute.put("description", route.getDescription());
        offlineRoute.put("transportMode", route.getTransportMode());
        offlineRoute.put("distanceKm", route.getDistanceKm());
        offlineRoute.put("durationMin", route.getDurationMin());
        offlineRoute.put("createdAt", route.getCreatedAt());
        offlineRoute.put("updatedAt", route.getUpdatedAt());

        List<Map<String, Object>> days = new ArrayList<>();
        if (route.getRouteDays() != null) {
            for (RouteDay day : route.getRouteDays()) {
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("dayNumber", day.getDayNumber());
                d.put("description", day.getDescription());
                d.put("plannedStart", day.getPlannedStart());
                d.put("plannedEnd", day.getPlannedEnd());

                List<Map<String, Object>> points = new ArrayList<>();
                if (day.getRoutePoints() != null) {
                    for (RoutePoint point : day.getRoutePoints()) {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("orderIndex", point.getOrderIndex());
                        p.put("poiId", point.getPoiId());
                        p.put("estimatedDuration", point.getEstimatedDuration());

                        PoiResponse poi = poiMap.get(point.getPoiId());
                        if (poi != null) {
                            Map<String, Object> poiInfo = new LinkedHashMap<>();
                            poiInfo.put("name", poi.getName());
                            poiInfo.put("address", poi.getAddress());
                            poiInfo.put("latitude", poi.getLatitude());
                            poiInfo.put("longitude", poi.getLongitude());
                            poiInfo.put("type", poi.getType());
                            poiInfo.put("coverUrl", poi.getCoverUrl());
                            p.put("poiInfo", poiInfo);
                        }
                        points.add(p);
                    }
                }
                d.put("points", points);
                days.add(d);
            }
        }

        offlineRoute.put("days", days);
        return offlineRoute;
    }

    private Map<String, Object> convertPoiDetails(Map<Long, PoiResponse> poiMap) {
        Map<Long, Map<String, Object>> detailed = new LinkedHashMap<>();
        for (PoiResponse poi : poiMap.values()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", poi.getName());
            d.put("description", poi.getDescription());
            d.put("address", poi.getAddress());
            d.put("latitude", poi.getLatitude());
            d.put("longitude", poi.getLongitude());
            d.put("phone", poi.getPhone());
            d.put("siteUrl", poi.getSiteUrl());
            d.put("priceLevel", poi.getPriceLevel());
            d.put("averageRating", poi.getAverageRating());
            d.put("type", poi.getType());
            d.put("coverUrl", poi.getCoverUrl());
            detailed.put(poi.getId(), d);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("pois", detailed);
        return root;
    }

    private String generateMapData(Route route, Map<Long, PoiResponse> poiMap) {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> points = new ArrayList<>();

        if (route.getRouteDays() != null) {
            for (RouteDay day : route.getRouteDays()) {
                if (day.getRoutePoints() != null) {
                    for (RoutePoint point : day.getRoutePoints()) {
                        Map<String, Object> mp = new LinkedHashMap<>();
                        mp.put("day", day.getDayNumber());
                        mp.put("order", point.getOrderIndex());
                        mp.put("poiId", point.getPoiId());

                        PoiResponse poi = poiMap.get(point.getPoiId());
                        if (poi != null) {
                            mp.put("name", poi.getName());
                            mp.put("lat", poi.getLatitude());
                            mp.put("lng", poi.getLongitude());
                            mp.put("type", poi.getType());
                        }
                        points.add(mp);
                    }
                }
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
        } catch (JsonProcessingException e) {
            log.error("Error generating map data json", e);
            return "{}";
        }
    }

    private void addToZip(ZipOutputStream zos, String filename, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private double calculateCenterLat(List<Map<String, Object>> points) {
        if (points.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (Map<String, Object> p : points) {
            Object lat = p.get("lat");
            if (lat instanceof Number n) { sum += n.doubleValue(); count++; }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double calculateCenterLng(List<Map<String, Object>> points) {
        if (points.isEmpty()) return 0.0;
        double sum = 0.0;
        int count = 0;
        for (Map<String, Object> p : points) {
            Object lng = p.get("lng");
            if (lng instanceof Number n) { sum += n.doubleValue(); count++; }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private int calculateZoomLevel(List<Map<String, Object>> points) {
        if (points.size() < 2) return 12;
        if (points.size() <= 5) return 13;
        if (points.size() <= 10) return 12;
        if (points.size() <= 20) return 11;
        return 10;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации JSON", e);
        }
    }

    private String routeZipKey(Long userId, Object routeId) {
        return keyPrefix + ":route:" + userId + ":" + routeId;
    }

    private String routeMetaKey(Long userId, Object routeId) {
        return keyPrefix + ":meta:" + userId + ":" + routeId;
    }

    private List<String> scanKeys(String pattern) {
        return offlineStringRedisTemplate.execute((RedisCallback<List<String>>) connection -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    private long getUserStorageUsageBytes(Long userId) {
        // Суммируем sizeBytes из meta (правильнее, чем дергать ZIP длину)
        String pattern = routeMetaKey(userId, "*");
        List<String> metaKeys = scanKeys(pattern);

        long sum = 0;
        for (String metaKey : metaKeys) {
            String metaJson = offlineStringRedisTemplate.opsForValue().get(metaKey);
            if (metaJson == null) continue;
            try {
                OfflineRouteMeta meta = objectMapper.readValue(metaJson, OfflineRouteMeta.class);
                sum += meta.getSizeBytes();
            } catch (Exception ignored) {}
        }
        return sum;
    }

    private int countUserOfflineRoutes(Long userId) {
        String pattern = routeMetaKey(userId, "*");
        return scanKeys(pattern).size();
    }
}