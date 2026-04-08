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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
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
        Long graphVersionId = null;

        int fallbackSegments = 0;

        for (int i = 1; i < points.size(); i++) {
            RoutingPoint fromPoint = points.get(i - 1);
            RoutingPoint toPoint = points.get(i);
            RoutingSegmentResult segment = graphRoutingService.buildSegment(cityId, fromPoint, toPoint, transportMode);
            segments.add(segment);
            totalDistance = totalDistance.add(segment.getDistanceKm() == null ? BigDecimal.ZERO : segment.getDistanceKm());
            totalDuration += segment.getDurationMin() == null ? 0 : segment.getDurationMin();
            mergeCoordinates(dayCoordinates, segment.getCoordinates());
            if (!"GRAPH".equals(segment.getGeometrySource())) {
                graphUsedForAll = false;
                fallbackSegments++;
                log.warn("Day route segment fallback: cityId={}, mode={}, fromRoutePointId={}, toRoutePointId={}, fromPoiId={}, toPoiId={}, status={}, diagnosticCode={}, graphVersionId={}",
                        cityId,
                        transportMode,
                        fromPoint.getRoutePointId(),
                        toPoint.getRoutePointId(),
                        fromPoint.getPoiId(),
                        toPoint.getPoiId(),
                        segment.getStatus(),
                        segment.getDiagnosticCode() != null ? segment.getDiagnosticCode() : segment.getDebugReason(),
                        segment.getGraphVersionId());
            }
            if (segment.getGraphVersionId() != null) {
                graphVersionId = segment.getGraphVersionId();
            }
        }

        result.setSegments(segments);
        result.setDayCoordinates(dayCoordinates);
        result.setTotalDistanceKm(totalDistance.setScale(2, RoundingMode.HALF_UP));
        result.setTotalDurationMin(totalDuration);
        result.setProvider("INTERNAL_GRAPH");
        result.setGeometrySource(graphUsedForAll ? "GRAPH" : "FALLBACK");
        result.setGraphVersionId(graphVersionId);

        if (fallbackSegments > 0) {
            log.warn("Day route built with fallback segments: cityId={}, mode={}, totalSegments={}, fallbackSegments={}, graphVersionId={}",
                    cityId, transportMode, segments.size(), fallbackSegments, graphVersionId);
        } else {
            log.info("Day route built fully on graph: cityId={}, mode={}, totalSegments={}, graphVersionId={}",
                    cityId, transportMode, segments.size(), graphVersionId);
        }
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
