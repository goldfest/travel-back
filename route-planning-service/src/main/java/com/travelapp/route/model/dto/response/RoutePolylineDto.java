package com.travelapp.route.model.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class RoutePolylineDto {
    private String source; // GRAPH / STRAIGHT / FALLBACK
    private List<LatLngDto> coordinates;
}
