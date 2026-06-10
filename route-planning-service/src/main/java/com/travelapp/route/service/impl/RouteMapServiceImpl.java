package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.client.PoiClient;
import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.exception.RouteValidationException;
import com.travelapp.route.model.dto.request.OneTimeRouteRequest;
import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.response.MapViewportDto;
import com.travelapp.route.model.dto.response.RouteMapDayResponse;
import com.travelapp.route.model.dto.response.RouteMapPointResponse;
import com.travelapp.route.model.dto.response.RouteMapResponse;
import com.travelapp.route.model.dto.response.RoutePolylineDto;
import com.travelapp.route.model.dto.response.RouteSegmentResponse;
import com.travelapp.route.model.dto.response.PoiResponse;
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
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.RouteMapService;
import com.travelapp.route.service.RoutePathCacheService;
import com.travelapp.route.service.RoutingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RouteMapServiceImpl implements RouteMapService {

    private final RouteRepository routeRepository;
    private final RouteDayPathRepository routeDayPathRepository;
    private final RouteSegmentPathRepository routeSegmentPathRepository;
    private final RoutePathCacheService routePathCacheService;
    private final RoutingProvider routingProvider;
    private final GraphVersionService graphVersionService;
    private final PoiClient poiClient;
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



    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RouteMapResponse buildOneTimeRouteToPoi(Long userId, OneTimeRouteRequest request) {
        PoiResponse poi;
        try {
            poi = poiClient.getPoiById(request.getToPoiId());
        } catch (Exception ex) {
            throw new ResourceNotFoundException("Объект не найден");
        }

        if (poi == null || poi.getLatitude() == null || poi.getLongitude() == null) {
            throw new RouteValidationException("У выбранного объекта нет координат для построения маршрута");
        }
        if (!request.getCityId().equals(poi.getCityId())) {
            throw new RouteValidationException("Объект не принадлежит выбранному городу");
        }

        Route.TransportMode transportMode = request.getTransportMode() != null
                ? request.getTransportMode()
                : Route.TransportMode.WALK;

        RoutingPoint from = new RoutingPoint(-1L, null, request.getFromLatitude(), request.getFromLongitude());
        RoutingPoint to = new RoutingPoint(-2L, poi.getId(), poi.getLatitude(), poi.getLongitude());

        RoutingDayResult result;
        if (!graphVersionService.hasActiveVersion(request.getCityId())) {
            log.warn("One-time route fallback: no active graph version for cityId={}, toPoiId={}",
                    request.getCityId(), request.getToPoiId());
            result = buildDirectOneTimeResult(request, poi, transportMode);
        } else {
            try {
                result = routingProvider.buildDayRoute(request.getCityId(), List.of(from, to), transportMode);
            } catch (Exception ex) {
                log.warn("One-time route graph calculation failed, fallback direct polyline will be returned: cityId={}, toPoiId={}, fromLat={}, fromLng={}, mode={}",
                        request.getCityId(), request.getToPoiId(), request.getFromLatitude(), request.getFromLongitude(), transportMode, ex);
                result = buildDirectOneTimeResult(request, poi, transportMode);
            }

            if (result == null) {
                result = buildDirectOneTimeResult(request, poi, transportMode);
            }
        }

        RouteMapPointResponse start = new RouteMapPointResponse();
        start.setRoutePointId(-1L);
        start.setPoiId(-1L);
        start.setOrderIndex((short) 1);
        start.setPoiName(request.getFromTitle() != null && !request.getFromTitle().isBlank() ? request.getFromTitle() : "Точка старта");
        start.setLatitude(request.getFromLatitude());
        start.setLongitude(request.getFromLongitude());
        start.setMarkerType("START");

        RouteMapPointResponse end = new RouteMapPointResponse();
        end.setRoutePointId(-2L);
        end.setPoiId(poi.getId());
        end.setOrderIndex((short) 2);
        end.setPoiName(poi.getName());
        end.setPoiAddress(poi.getAddress());
        end.setPoiType(poi.getPoiType() != null ? poi.getPoiType().getCode() : null);
        end.setLatitude(poi.getLatitude());
        end.setLongitude(poi.getLongitude());
        end.setMarkerType("END");

        RouteMapDayResponse day = new RouteMapDayResponse();
        day.setRouteDayId(0L);
        day.setDayNumber((short) 1);
        day.setPoints(List.of(start, end));

        RoutePolylineDto polyline = new RoutePolylineDto();
        polyline.setSource(result.getGeometrySource());
        List<LatLngDto> coordinates = result.getDayCoordinates() != null && !result.getDayCoordinates().isEmpty()
                ? result.getDayCoordinates()
                : List.of(
                new LatLngDto(request.getFromLatitude(), request.getFromLongitude()),
                new LatLngDto(poi.getLatitude(), poi.getLongitude())
        );
        polyline.setCoordinates(coordinates);
        day.setPolyline(polyline);
        day.setSegments(result.getSegments() == null ? List.of() : result.getSegments().stream()
                .map(segment -> toSegmentResponse(segment, transportMode))
                .toList());

        RouteMapResponse response = new RouteMapResponse();
        response.setRouteId(0L);
        response.setRouteName("Маршрут до «" + poi.getName() + "»");
        response.setDescription("Разовый маршрут не сохраняется в базе данных");
        response.setTransportMode(transportMode.name());
        response.setTotalDistanceKm(result.getTotalDistanceKm());
        response.setTotalDurationMin(result.getTotalDurationMin());
        response.setDays(List.of(day));
        response.setViewport(buildViewport(coordinates));
        return response;
    }

    private RoutingDayResult buildDirectOneTimeResult(OneTimeRouteRequest request, PoiResponse poi, Route.TransportMode transportMode) {
        List<LatLngDto> coordinates = List.of(
                new LatLngDto(request.getFromLatitude(), request.getFromLongitude()),
                new LatLngDto(poi.getLatitude(), poi.getLongitude())
        );

        BigDecimal distanceKm = BigDecimal.valueOf(calculateDistanceKm(
                request.getFromLatitude(),
                request.getFromLongitude(),
                poi.getLatitude(),
                poi.getLongitude()
        )).setScale(2, RoundingMode.HALF_UP);

        int speedKmH = switch (transportMode) {
            case CAR -> 35;
            case PUBLIC_TRANSPORT -> 25;
            case MIXED -> 20;
            case WALK -> 5;
        };
        int durationMin = Math.max(1, (int) Math.ceil(distanceKm.doubleValue() / speedKmH * 60.0d));

        RoutingSegmentResult segment = new RoutingSegmentResult();
        segment.setFromRoutePointId(-1L);
        segment.setToRoutePointId(-2L);
        segment.setDistanceKm(distanceKm);
        segment.setDurationMin(durationMin);
        segment.setProvider("INTERNAL_GRAPH");
        segment.setGeometrySource("FALLBACK");
        segment.setStatus("FALLBACK");
        segment.setDiagnosticCode("ONE_TIME_ROUTE_FALLBACK");
        segment.setCoordinates(coordinates);

        RoutingDayResult result = new RoutingDayResult();
        result.setTotalDistanceKm(distanceKm);
        result.setTotalDurationMin(durationMin);
        result.setProvider("INTERNAL_GRAPH");
        result.setGeometrySource("FALLBACK");
        result.setDayCoordinates(coordinates);
        result.setSegments(List.of(segment));
        return result;
    }

    private double calculateDistanceKm(double fromLat, double fromLng, double toLat, double toLng) {
        final double earthRadiusKm = 6371.0d;
        double dLat = Math.toRadians(toLat - fromLat);
        double dLng = Math.toRadians(toLng - fromLng);
        double a = Math.sin(dLat / 2.0d) * Math.sin(dLat / 2.0d)
                + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat))
                * Math.sin(dLng / 2.0d) * Math.sin(dLng / 2.0d);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return earthRadiusKm * c;
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

    private RouteSegmentResponse toSegmentResponse(RoutingSegmentResult path, Route.TransportMode transportMode) {
        RouteSegmentResponse segment = new RouteSegmentResponse();
        segment.setFromRoutePointId(path.getFromRoutePointId());
        segment.setToRoutePointId(path.getToRoutePointId());
        segment.setDistanceKm(path.getDistanceKm() != null ? path.getDistanceKm().doubleValue() : null);
        segment.setDurationMin(path.getDurationMin());
        segment.setTransportMode(transportMode.name());
        segment.setProvider(path.getProvider());
        segment.setStatus(path.getStatus());
        segment.setDiagnosticCode(path.getDiagnosticCode());

        RoutePolylineDto polyline = new RoutePolylineDto();
        polyline.setSource(path.getGeometrySource());
        polyline.setCoordinates(path.getCoordinates() == null ? List.of() : path.getCoordinates());
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
