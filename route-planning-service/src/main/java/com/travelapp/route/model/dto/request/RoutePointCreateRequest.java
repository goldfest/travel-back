package com.travelapp.route.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
@Data
public class RoutePointCreateRequest {

    @NotNull
    private Long poiId;

    @NotNull
    private Short orderIndex;

    @NotNull
    @Min(5)
    @Max(1440)
    private Integer estimatedVisitMinutes;

    private LocalDateTime plannedArrival;
    private LocalDateTime plannedDeparture;
}