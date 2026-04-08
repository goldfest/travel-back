package com.travelapp.route.model.dto.response;

public record CityGraphStatusResponse(
        Long cityId,
        boolean downloaded,
        boolean downloading,
        boolean ready,
        String status
) {
}
