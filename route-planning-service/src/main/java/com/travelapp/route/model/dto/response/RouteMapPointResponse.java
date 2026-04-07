package com.travelapp.route.model.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RouteMapPointResponse {
    private Long routePointId;
    private Long poiId;
    private Short orderIndex;

    private String poiName;
    private String poiAddress;
    private String poiType;

    private Double latitude;
    private Double longitude;

    private String markerType; // START, WAYPOINT, END
    private Integer estimatedVisitMinutes;

    private LocalDateTime plannedArrivalAt;
    private LocalDateTime plannedDepartureAt;
}