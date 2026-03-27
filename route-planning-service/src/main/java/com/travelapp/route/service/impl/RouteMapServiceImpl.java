package com.travelapp.route.service.impl;

import com.travelapp.route.exception.ResourceNotFoundException;
import com.travelapp.route.model.dto.response.*;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.model.entity.RouteDay;
import com.travelapp.route.model.entity.RoutePoint;
import com.travelapp.route.repository.RouteRepository;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.RouteMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RouteMapServiceImpl implements RouteMapService {

    private final RouteRepository routeRepository;
    private final DistanceCalculationService distanceCalculationService;

    @Override
    public RouteMapResponse getRouteMap(Long userId, Long routeId) {
        Route route = routeRepository.findByIdAndUserId(routeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Маршрут не найден"));

        RouteMapResponse response = new RouteMapResponse();
        response.setRouteId(route.getId());
        response.setRouteName(route.getName());
        response.setDescription(route.getDescription());
        response.setTransportMode(route.getTransportMode().name());
        response.setTotalDistanceKm(route.getDistanceKm());
        response.setTotalDurationMin(route.getDurationMin());

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

                if (i == 0) dto.setMarkerType("START");
                else if (i == points.size() - 1) dto.setMarkerType("END");
                else dto.setMarkerType("WAYPOINT");

                mapPoints.add(dto);
                allCoordinates.add(new LatLngDto(point.getPoiLatitude(), point.getPoiLongitude()));
            }

            dayResponse.setPoints(mapPoints);
            dayResponse.setPolyline(buildStraightPolyline(points));
            dayResponse.setSegments(buildSegments(points, route.getTransportMode().name()));

            dayResponses.add(dayResponse);
        }

        response.setDays(dayResponses);
        response.setViewport(buildViewport(allCoordinates));

        return response;
    }

    private RoutePolylineDto buildStraightPolyline(List<RoutePoint> points) {
        RoutePolylineDto polyline = new RoutePolylineDto();
        polyline.setSource("STRAIGHT");
        polyline.setCoordinates(
                points.stream()
                        .map(p -> new LatLngDto(p.getPoiLatitude(), p.getPoiLongitude()))
                        .collect(Collectors.toList())
        );
        return polyline;
    }

    private List<RouteSegmentResponse> buildSegments(List<RoutePoint> points, String transportMode) {
        List<RouteSegmentResponse> segments = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            RoutePoint prev = points.get(i - 1);
            RoutePoint curr = points.get(i);

            double[] p1 = {prev.getPoiLatitude(), prev.getPoiLongitude()};
            double[] p2 = {curr.getPoiLatitude(), curr.getPoiLongitude()};

            DistanceCalculationService.TravelInfo info =
                    distanceCalculationService.calculateTravelInfo(p1, p2, transportMode);

            RouteSegmentResponse segment = new RouteSegmentResponse();
            segment.setFromRoutePointId(prev.getId());
            segment.setToRoutePointId(curr.getId());
            segment.setDistanceKm(info.getDistanceKm());
            segment.setDurationMin(info.getTimeMinutes());
            segment.setTransportMode(transportMode);

            segments.add(segment);
        }
        return segments;
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