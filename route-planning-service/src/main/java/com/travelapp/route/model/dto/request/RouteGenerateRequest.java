package com.travelapp.route.model.dto.request;

import com.travelapp.route.model.entity.Route;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RouteGenerateRequest {
    @NotNull
    private Long cityId;

    @Min(1)
    @Max(14)
    private Integer daysCount;

    private List<String> interests;

    private Integer budgetLevel;

    private Route.TransportMode transportMode = Route.TransportMode.WALK;

    private Boolean optimize = true;
}