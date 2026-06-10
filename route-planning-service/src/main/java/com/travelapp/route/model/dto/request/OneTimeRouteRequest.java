package com.travelapp.route.model.dto.request;

import com.travelapp.route.model.entity.Route;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OneTimeRouteRequest {
    @NotNull
    private Long cityId;

    @NotNull
    private Long toPoiId;

    @NotNull
    private Double fromLatitude;

    @NotNull
    private Double fromLongitude;

    private String fromTitle;

    private Route.TransportMode transportMode = Route.TransportMode.WALK;
}
