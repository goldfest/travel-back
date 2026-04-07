package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.response.MapViewportDto;
import com.travelapp.route.model.dto.response.RouteMapDayResponse;
import com.travelapp.route.model.dto.response.RouteMapPointResponse;
import com.travelapp.route.model.dto.response.RouteMapResponse;
import com.travelapp.route.model.dto.response.RoutePolylineDto;
import com.travelapp.route.model.dto.response.RouteSegmentResponse;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RouteDayPath;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.model.entity.RouteSegmentPath;
import com.travelapp.route.repository.RouteDayPathRepository;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.repository.RouteSegmentPathRepository;
import com.travelapp.route.service.RouteMapService;
import com.travelapp.route.service.RoutePathCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RouteMapServiceImpl implements RouteMapService {

    private final RouteRepository routeRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RouteSegmentPathRepository routeSegmentPathRepository;
    private final RoutePathCacheService routePathCacheService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RouteMapResponse getRouteMap(Long userId, Long routeId) {
        Route route = routeRepository.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        if (route.getStatus() == Route.RouteStatus.GRAPH_PREPARING) {
            return buildPendingMap(route);
        }

        boolean cacheMissingOrStale = route.getRouteDays().stream()
                .anyMatch(day -> routeDayPathRepository.findByRouteDayId(day.getId()).isEmpty())
                || !routePathCacheService.isRouteCacheActual(route.getId());

        if (cacheMissingOrStale) {
            routePathCacheService.rebuildRoutePaths(route.getId());
            route = routeRepository.findByIdAndUserId(routeId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));
        }

        RouteMapResponse response = new RouteMapResponse();
        response.setRouteId(route.getId());
        response.setRouteName(route.getName());
        response.setDescription(route.getDescription());
        response.setTransportMode(route.getTransportMode().name());

        List<RouteDay> sortedDays = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .toList();

        List<RouteMapDayResponse> dayResponses = new ArrayList<>();
        List<LatLngDto> allCoordinates = new ArrayList<>();

        double totalDistance = 0.0;
        int totalDuration = 0;

        for (RouteDay day : sortedDays) {
            List<RoutePoint> points = day.getRoutePoints().stream()
                    .filter(p -> p.getPoiLatitude() != null && p.getPoiLongitude() != null)
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            RouteMapDayResponse dayResponse = new RouteMapDayResponse();
            dayResponse.setRouteDayId(day.getId());
            dayResponse.setDayNumber(day.getDayNumber());

            List<RouteMapPointResponse> mapPoints = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                RoutePoint point = points.get(i);

                RouteMapPointResponse dto = new RouteMapPointResponse();
                dto.setRoutePointId(point.getId());
                dto.setPoiId(point.getPoiId());
                dto.setOrderIndex(point.getOrderIndex());
                dto.setPoiName(point.getPoiName());
                dto.setPoiAddress(point.getPoiAddress());
                dto.setPoiType(point.getPoiType());
                dto.setLatitude(point.getPoiLatitude());
                dto.setLongitude(point.getPoiLongitude());
                dto.setEstimatedVisitMinutes(point.getEstimatedVisitMinutes());
                dto.setPlannedArrivalAt(point.getPlannedArrivalAt());
                dto.setPlannedDepartureAt(point.getPlannedDepartureAt());

                if (i == 0) {
                    dto.setMarkerType("START");
                } else if (i == points.size() - 1) {
                    dto.setMarkerType("END");
                } else {
                    dto.setMarkerType("WAYPOINT");
                }

                mapPoints.add(dto);
            }

            dayResponse.setPoints(mapPoints);

            RouteDayPath dayPath = routeDayPathRepository.findByRouteDayId(day.getId()).orElse(null);
            if (dayPath != null) {
                RoutePolylineDto polyline = new RoutePolylineDto();
                polyline.setSource(dayPath.getGeometrySource());
                polyline.setCoordinates(readCoordinates(dayPath.getPolylineJson()));
                dayResponse.setPolyline(polyline);

                if (dayPath.getPolylineJson() != null) {
                    allCoordinates.addAll(readCoordinates(dayPath.getPolylineJson()));
                }

                if (dayPath.getDistanceKm() != null) {
                    totalDistance += dayPath.getDistanceKm().doubleValue();
                }
                if (dayPath.getDurationMin() != null) {
                    totalDuration += dayPath.getDurationMin();
                }
            } else {
                allCoordinates.addAll(points.stream()
                        .map(p -> new LatLngDto(p.getPoiLatitude(), p.getPoiLongitude()))
                        .toList());
            }

            List<RouteSegmentPath> segmentPaths = routeSegmentPathRepository.findByRouteDayIdOrderBySegmentOrderAsc(day.getId());
            List<RouteSegmentResponse> segments = segmentPaths.stream()
                    .map(this::toSegmentResponse)
                    .toList();

            dayResponse.setSegments(segments);
            dayResponses.add(dayResponse);
        }

        response.setDays(dayResponses);
        response.setViewport(buildViewport(allCoordinates));

        if (totalDistance > 0) {
            response.setTotalDistanceKm(BigDecimal.valueOf(totalDistance).setScale(2, RoundingMode.HALF_UP));
        } else {
            response.setTotalDistanceKm(route.getDistanceKm());
        }

        response.setTotalDurationMin(totalDuration > 0 ? totalDuration : route.getDurationMin());
        return response;
    }


    private RouteMapResponse buildPendingMap(Route route) {
        RouteMapResponse response = new RouteMapResponse();
        response.setRouteId(route.getId());
        response.setRouteName(route.getName());
        response.setDescription(route.getDescription());
        response.setTransportMode(route.getTransportMode().name());

        List<RouteDay> sortedDays = route.getRouteDays().stream()
                .sorted(Comparator.comparing(RouteDay::getDayNumber))
                .toList();

        List<RouteMapDayResponse> dayResponses = new ArrayList<>();
        List<LatLngDto> allCoordinates = new ArrayList<>();

        for (RouteDay day : sortedDays) {
            List<RoutePoint> points = day.getRoutePoints().stream()
                    .filter(p -> p.getPoiLatitude() != null && p.getPoiLongitude() != null)
                    .sorted(Comparator.comparing(RoutePoint::getOrderIndex))
                    .toList();

            RouteMapDayResponse dayResponse = new RouteMapDayResponse();
            dayResponse.setRouteDayId(day.getId());
            dayResponse.setDayNumber(day.getDayNumber());
            dayResponse.setPoints(points.stream().map(point -> {
                RouteMapPointResponse dto = new RouteMapPointResponse();
                dto.setRoutePointId(point.getId());
                dto.setPoiId(point.getPoiId());
                dto.setOrderIndex(point.getOrderIndex());
                dto.setPoiName(point.getPoiName());
                dto.setPoiAddress(point.getPoiAddress());
                dto.setPoiType(point.getPoiType());
                dto.setLatitude(point.getPoiLatitude());
                dto.setLongitude(point.getPoiLongitude());
                dto.setEstimatedVisitMinutes(point.getEstimatedVisitMinutes());
                dto.setPlannedArrivalAt(point.getPlannedArrivalAt());
                dto.setPlannedDepartureAt(point.getPlannedDepartureAt());
                return dto;
            }).toList());

            List<LatLngDto> fallbackCoordinates = points.stream()
                    .map(p -> new LatLngDto(p.getPoiLatitude(), p.getPoiLongitude()))
                    .toList();

            RoutePolylineDto polyline = new RoutePolylineDto();
            polyline.setSource("GRAPH_PREPARING");
            polyline.setCoordinates(fallbackCoordinates);
            dayResponse.setPolyline(polyline);

            allCoordinates.addAll(fallbackCoordinates);
            dayResponses.add(dayResponse);
        }

        response.setDays(dayResponses);
        response.setViewport(buildViewport(allCoordinates));
        response.setTotalDistanceKm(route.getDistanceKm());
        response.setTotalDurationMin(route.getDurationMin());
        return response;
    }

    private RouteSegmentResponse toSegmentResponse(RouteSegmentPath path) {
        RouteSegmentResponse segment = new RouteSegmentResponse();
        segment.setFromRoutePointId(path.getFromRoutePoint().getId());
        segment.setToRoutePointId(path.getToRoutePoint().getId());
        segment.setDistanceKm(path.getDistanceKm() != null ? path.getDistanceKm().doubleValue() : null);
        segment.setDurationMin(path.getDurationMin());
        segment.setTransportMode(path.getTransportMode().name());
        segment.setProvider(path.getProvider());
        segment.setStatus(path.getStatus());
        segment.setDiagnosticCode(path.getDiagnosticCode());

        RoutePolylineDto polyline = new RoutePolylineDto();
        polyline.setSource(path.getGeometrySource());
        polyline.setCoordinates(readCoordinates(path.getPolylineJson()));
        segment.setPolyline(polyline);
        return segment;
    }

    private List<LatLngDto> readCoordinates(String json) {
        try {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<List<LatLngDto>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось прочитать polyline_json", e);
        }
    }

    private MapViewportDto buildViewport(List<LatLngDto> coordinates) {
        MapViewportDto viewport = new MapViewportDto();
        if (coordinates == null || coordinates.isEmpty()) {
            return viewport;
        }

        double minLat = coordinates.stream().mapToDouble(LatLngDto::getLatitude).min().orElse(0);
        double maxLat = coordinates.stream().mapToDouble(LatLngDto::getLatitude).max().orElse(0);
        double minLng = coordinates.stream().mapToDouble(LatLngDto::getLongitude).min().orElse(0);
        double maxLng = coordinates.stream().mapToDouble(LatLngDto::getLongitude).max().orElse(0);

        viewport.setMinLat(minLat);
        viewport.setMaxLat(maxLat);
        viewport.setMinLng(minLng);
        viewport.setMaxLng(maxLng);
        viewport.setCenterLat((minLat + maxLat) / 2.0);
        viewport.setCenterLng((minLng + maxLng) / 2.0);
        return viewport;
    }
}
