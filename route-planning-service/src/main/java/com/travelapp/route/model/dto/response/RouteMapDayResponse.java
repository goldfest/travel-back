package com.travelapp.route.model.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class RouteMapDayResponse {
    private Long routeDayId;
    private Short dayNumber;
    private RoutePolylineDto polyline;
    private List<RouteMapPointResponse> points;
    private List<RouteSegmentResponse> segments;
}