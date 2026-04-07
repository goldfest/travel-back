package com.travelapp.route.service.impl;

import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.routing.RoutingDayResult;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.service.GraphRoutingService;
import com.travelapp.route.service.RoutingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InternalGraphRoutingProvider implements RoutingProvider {

    private final GraphRoutingService graphRoutingService;

    @Override
    public RoutingDayResult buildDayRoute(Long cityId, List<RoutingPoint> points, Route.TransportMode transportMode) {
        RoutingDayResult result = new RoutingDayResult();

        if (points == null || points.size() < 2) {
            result.setSegments(List.of());
            result.setDayCoordinates(points == null ? List.of() : points.stream()
                    .map(p -> new LatLngDto(p.getLatitude(), p.getLongitude()))
                    .toList());
            result.setTotalDistanceKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            result.setTotalDurationMin(0);
            result.setProvider("INTERNAL_GRAPH");
            result.setGeometrySource("FALLBACK");
            return result;
        }

        List<RoutingSegmentResult> segments = new ArrayList<>();
        List<LatLngDto> dayCoordinates = new ArrayList<>();
        BigDecimal totalDistance = BigDecimal.ZERO;
        int totalDuration = 0;
        boolean graphUsedForAll = true;

        for (int i = 1; i < points.size(); i++) {
            RoutingSegmentResult segment = graphRoutingService.buildSegment(cityId, points.get(i - 1), points.get(i), transportMode);
            segments.add(segment);
            totalDistance = totalDistance.add(segment.getDistanceKm() == null ? BigDecimal.ZERO : segment.getDistanceKm());
            totalDuration += segment.getDurationMin() == null ? 0 : segment.getDurationMin();
            mergeCoordinates(dayCoordinates, segment.getCoordinates());
            if (!"GRAPH".equals(segment.getGeometrySource())) {
                graphUsedForAll = false;
            }
        }

        result.setSegments(segments);
        result.setDayCoordinates(dayCoordinates);
        result.setTotalDistanceKm(totalDistance.setScale(2, RoundingMode.HALF_UP));
        result.setTotalDurationMin(totalDuration);
        result.setProvider("INTERNAL_GRAPH");
        result.setGeometrySource(graphUsedForAll ? "GRAPH" : "FALLBACK");
        return result;
    }

    @Override
    public TravelMatrixResult buildMatrix(Long cityId, List<RoutingPoint> points, Route.TransportMode transportMode) {
        return graphRoutingService.buildMatrix(cityId, points, transportMode);
    }

    private void mergeCoordinates(List<LatLngDto> merged, List<LatLngDto> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        if (merged.isEmpty()) {
            merged.addAll(candidate);
            return;
        }

        LatLngDto last = merged.get(merged.size() - 1);
        LatLngDto first = candidate.get(0);
        int startIndex = (Double.compare(last.getLatitude(), first.getLatitude()) == 0
                && Double.compare(last.getLongitude(), first.getLongitude()) == 0) ? 1 : 0;
        for (int i = startIndex; i < candidate.size(); i++) {
            merged.add(candidate.get(i));
        }
    }
}
