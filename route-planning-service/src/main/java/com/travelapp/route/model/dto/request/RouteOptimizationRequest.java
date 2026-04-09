package com.travelapp.route.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Запрос на оптимизацию маршрута")
public class RouteOptimizationRequest {

    @Schema(description = "Режим оптимизации", allowableValues = {"TIME_WINDOW", "USER_ORDER"}, example = "TIME_WINDOW")
    private String optimizationMode = "TIME_WINDOW";

    @Schema(description = "Настройки дней маршрута")
    private List<RouteOptimizationDayRequest> daySettings = new ArrayList<>();

    @Schema(description = "Переопределение времени посещения по routePointId -> минуты")
    private Map<Long, Integer> visitMinutesByRoutePointId;

    @Data
    public static class RouteOptimizationDayRequest {
        @Schema(description = "ID дня маршрута", example = "15")
        private Long routeDayId;

        @Schema(description = "Дата дня", example = "2026-04-15")
        private LocalDate routeDate;

        @Schema(description = "Начало дня", example = "08:00")
        private LocalTime dayStartTime;

        @Schema(description = "Окончание дня", example = "18:00")
        private LocalTime dayEndTime;
    }
}
