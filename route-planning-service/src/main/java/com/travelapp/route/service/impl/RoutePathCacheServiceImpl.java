package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.dto.routing.RoutingDayResult;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RouteDayPath;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.model.entity.RouteSegmentPath;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.repository.RouteSegmentPathRepository;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.RoutingProvider;
import com.travelapp.route.service.RoutePathCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutePathCacheServiceImpl implements RoutePathCacheService {

    private final RouteRepository routeRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RouteSegmentPathRepository routeSegmentPathRepository;
    private final RoutingProvider routingProvider;
    private final GraphRoutingServiceImpl graphRoutingService;
    private final GraphVersionService graphVersionService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void rebuildRoutePaths(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        CityGraphVersion activeVersion = graphVersionService.getActiveVersionOrThrow(route.getCityId());
        invalidateRoutePaths(routeId);

        for (RouteDay day : route.getRouteDays()) {
            List<RoutePoint> points = day.getRoutePoints().stream()
                    .filter(p -> p.getPoiLatitude() != null && p.getPoiLongitude() != null)
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            if (points.isEmpty()) {
                continue;
            }

            Map<Long, RoutePoint> pointMap = points.stream()
                    .collect(Collectors.toMap(RoutePoint::getId, Function.identity()));

            List<RoutingPoint> routingPoints = points.stream()
                    .map(p -> new RoutingPoint(p.getId(), p.getPoiId(), p.getPoiLatitude(), p.getPoiLongitude()))
                    .toList();

            log.info("Rebuilding route day path: routeId={}, routeDayId={}, dayNumber={}, cityId={}, mode={}, points={}",
                    route.getId(), day.getId(), day.getDayNumber(), route.getCityId(), route.getTransportMode(), routingPoints.size());

            RoutingDayResult result = routingProvider.buildDayRoute(route.getCityId(), routingPoints, route.getTransportMode());

            long fallbackSegments = result.getSegments() == null ? 0 : result.getSegments().stream()
                    .filter(seg -> !"GRAPH".equals(seg.getGeometrySource()))
                    .count();
            String fallbackReasons = result.getSegments() == null ? "" : result.getSegments().stream()
                    .map(seg -> seg.getDiagnosticCode() != null ? seg.getDiagnosticCode() : seg.getDebugReason())
                    .filter(Objects::nonNull)
                    .filter(reason -> !"GRAPH_OK".equals(reason))
                    .distinct()
                    .collect(Collectors.joining(","));

            log.info("Route day path built: routeId={}, routeDayId={}, dayNumber={}, geometrySource={}, segments={}, fallbackSegments={}, fallbackReasons={}, graphVersionId={}",
                    route.getId(),
                    day.getId(),
                    day.getDayNumber(),
                    result.getGeometrySource(),
                    result.getSegments() == null ? 0 : result.getSegments().size(),
                    fallbackSegments,
                    fallbackReasons.isBlank() ? "-" : fallbackReasons,
                    result.getGraphVersionId());

            RouteDayPath dayPath = new RouteDayPath();
            dayPath.setRouteDay(day);
            dayPath.setGraphVersion(activeVersion);
            dayPath.setTransportMode(route.getTransportMode());
            dayPath.setProvider(result.getProvider());
            dayPath.setGeometrySource(result.getGeometrySource());
            dayPath.setDistanceKm(result.getTotalDistanceKm());
            dayPath.setDurationMin(result.getTotalDurationMin());
            dayPath.setBuiltAt(LocalDateTime.now());
            dayPath.setPolylineJson(toJson(result.getDayCoordinates()));
            routeDayPathRepository.save(dayPath);

            short order = 1;
            for (RoutingSegmentResult seg : result.getSegments()) {
                RouteSegmentPath entity = new RouteSegmentPath();
                entity.setRouteDay(day);
                entity.setFromRoutePoint(pointMap.get(seg.getFromRoutePointId()));
                entity.setToRoutePoint(pointMap.get(seg.getToRoutePointId()));
                entity.setGraphVersion(activeVersion);
                entity.setSegmentOrder(order++);
                entity.setTransportMode(route.getTransportMode());
                entity.setProvider(seg.getProvider());
                entity.setGeometrySource(seg.getGeometrySource());
                entity.setStatus(seg.getStatus());
                entity.setDiagnosticCode(seg.getDiagnosticCode() != null ? seg.getDiagnosticCode() : seg.getDebugReason());
                entity.setDistanceKm(seg.getDistanceKm());
                entity.setDurationMin(seg.getDurationMin());
                entity.setPolylineJson(toJson(seg.getCoordinates()));
                routeSegmentPathRepository.save(entity);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRouteCacheMissingOrStale(Route route) {
        if (route == null) {
            return true;
        }

        Long activeVersionId = graphVersionService.getRequiredActiveVersionId(route.getCityId());
        for (RouteDay day : route.getRouteDays()) {
            RouteDayPath dayPath = routeDayPathRepository.findByRouteDayId(day.getId()).orElse(null);
            if (dayPath == null || dayPath.getGraphVersion() == null || !activeVersionId.equals(dayPath.getGraphVersion().getId())) {
                return true;
            }

            List<RouteSegmentPath> segments = routeSegmentPathRepository.findByRouteDayIdOrderBySegmentOrderAsc(day.getId());
            long expectedSegments = Math.max(day.getRoutePoints().stream().filter(p -> p.getPoiLatitude() != null && p.getPoiLongitude() != null).count() - 1, 0);
            if (segments.size() != expectedSegments) {
                return true;
            }
            boolean hasStaleSegment = segments.stream().anyMatch(segment -> segment.getGraphVersion() == null || !activeVersionId.equals(segment.getGraphVersion().getId()));
            if (hasStaleSegment) {
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRouteCacheActual(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));
        return !isRouteCacheMissingOrStale(route);
    }

    @Override
    @Transactional
    public void invalidateRoutePaths(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        for (RouteDay day : route.getRouteDays()) {
            routeSegmentPathRepository.deleteByRouteDayId(day.getId());
            routeDayPathRepository.deleteByRouteDayId(day.getId());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать polyline_json", e);
        }
    }
}
