package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.dto.routing.RoutingDayResult;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RouteDayPath;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.model.entity.RouteSegmentPath;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.repository.RouteSegmentPathRepository;
import com.travelapp.route.service.RoutingProvider;
import com.travelapp.route.service.RoutePathCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutePathCacheServiceImpl implements RoutePathCacheService {

    private final RouteRepository routeRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RouteSegmentPathRepository routeSegmentPathRepository;
    private final RoutingProvider routingProvider;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void rebuildRoutePaths(Long routeId) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        invalidateRoutePaths(routeId);

        for (RouteDay day : route.getRouteDays()) {
            List<RoutePoint> points = day.getRoutePoints().stream()
                    .filter(p -> p.getPoiLatitude() != null && p.getPoiLongitude() != null)
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            if (points.isEmpty()) {
                continue;
            }

            List<RoutingPoint> routingPoints = points.stream()
                    .map(p -> new RoutingPoint(p.getId(), p.getPoiLatitude(), p.getPoiLongitude()))
                    .toList();

            RoutingDayResult result = routingProvider.buildDayRoute(routingPoints, route.getTransportMode());

            RouteDayPath dayPath = new RouteDayPath();
            dayPath.setRouteDay(day);
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
                entity.setFromRoutePoint(points.stream().filter(p -> p.getId().equals(seg.getFromRoutePointId())).findFirst().orElseThrow());
                entity.setToRoutePoint(points.stream().filter(p -> p.getId().equals(seg.getToRoutePointId())).findFirst().orElseThrow());
                entity.setSegmentOrder(order++);
                entity.setTransportMode(route.getTransportMode());
                entity.setProvider(seg.getProvider());
                entity.setGeometrySource(seg.getGeometrySource());
                entity.setStatus(seg.getStatus());
                entity.setDistanceKm(seg.getDistanceKm());
                entity.setDurationMin(seg.getDurationMin());
                entity.setPolylineJson(toJson(seg.getCoordinates()));
                routeSegmentPathRepository.save(entity);
            }
        }
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