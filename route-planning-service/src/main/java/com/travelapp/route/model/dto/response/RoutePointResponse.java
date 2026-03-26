package com.travelapp.route.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Ответ с информацией о точке маршрута")
public class RoutePointResponse {

    private Long id;
    private Short orderIndex;
    private Long poiId;
    private Long routeDayId;
    private String poiName;
    private String poiAddress;
    private Double poiLatitude;
    private Double poiLongitude;
    private String poiType;

    @Schema(description = "Оценочная продолжительность посещения в минутах", example = "120")
    private Integer estimatedVisitMinutes;

    private LocalDateTime plannedArrivalAt;
    private LocalDateTime plannedDepartureAt;
    private LocalDateTime createdAt;
}
