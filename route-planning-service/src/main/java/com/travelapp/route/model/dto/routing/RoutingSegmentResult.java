package com.travelapp.route.model.dto.routing;

import com.travelapp.route.model.dto.response.LatLngDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RoutingSegmentResult {
    private Long fromRoutePointId;
    private Long toRoutePointId;
    private BigDecimal distanceKm;
    private Integer durationMin;
    private String provider;
    private String geometrySource;
    private String status;
    private String diagnosticCode;
    private String debugReason;
    private Long graphVersionId;
    private List<LatLngDto> coordinates;
}
