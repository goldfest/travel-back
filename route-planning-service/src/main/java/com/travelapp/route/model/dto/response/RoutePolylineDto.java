package com.travelapp.route.model.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class RoutePolylineDto {
    private String source; // STRAIGHT
    private List<LatLngDto> coordinates;
}