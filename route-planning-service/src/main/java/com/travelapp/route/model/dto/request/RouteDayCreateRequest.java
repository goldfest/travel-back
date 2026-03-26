package com.travelapp.route.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RouteDayCreateRequest {

    @NotNull
    private Short dayNumber;

    private String description;

    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;

    @NotNull
    @Size(min = 1)
    private List<RoutePointCreateRequest> points;
}