package com.travelapp.route.model.dto.routing;

import com.travelapp.route.model.dto.response.LatLngDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoutingDayResult {
    private BigDecimal totalDistanceKm;
    private Integer totalDurationMin;
    private String provider;
    private String geometrySource;
    private Long graphVersionId;
    private List<LatLngDto> dayCoordinates;
    private List<RoutingSegmentResult> segments;
}
