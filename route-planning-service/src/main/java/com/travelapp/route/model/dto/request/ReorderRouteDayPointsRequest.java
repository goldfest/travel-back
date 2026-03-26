package com.travelapp.route.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ReorderRouteDayPointsRequest {
    @NotNull
    private List<Long> routePointIdsInOrder;
}