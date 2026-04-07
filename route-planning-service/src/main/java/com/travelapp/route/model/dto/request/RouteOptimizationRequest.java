package com.travelapp.route.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

@Data
@Schema(description = "Запрос на оптимизацию маршрута")
public class RouteOptimizationRequest {

    @Schema(description = "Режим оптимизации",
            allowableValues = {"TIME", "DISTANCE", "SCENIC", "RATING"},
            example = "TIME")
    private String optimizationMode = "TIME";

    @Schema(description = "Время начала дня", example = "09:00")
    private LocalTime dayStartTime;

    @Schema(description = "Время окончания дня", example = "18:00")
    private LocalTime dayEndTime;

    @Schema(description = "Максимальное расстояние между точками в метрах", example = "5000")
    private Integer maxDistanceBetweenPoints;

    @Schema(description = "Учитывать время работы объектов", example = "true")
    private Boolean considerOpeningHours = true;

    @Schema(description = "Учитывать обеденное время (12:00-14:00)", example = "true")
    private Boolean considerLunchBreak = true;
}