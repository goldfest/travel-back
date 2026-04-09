package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Schema(description = "Ответ с информацией о дне маршрута")
public class RouteDayResponse {

    @Schema(description = "ID дня маршрута", example = "1")
    private Long id;

    @Schema(description = "Номер дня", example = "1")
    private Short dayNumber;

    @Schema(description = "Дата дня")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate routeDate;

    @Schema(description = "Планируемое время начала")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime plannedStart;

    @Schema(description = "Планируемое время окончания")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime plannedEnd;

    @Schema(description = "Время начала дня")
    private LocalTime startTime;

    @Schema(description = "Время окончания дня")
    private LocalTime endTime;

    @Schema(description = "Описание дня")
    private String description;

    @Schema(description = "Примерная продолжительность дня в часах", example = "8")
    private Integer estimatedDurationHours;

    @Schema(description = "Количество точек в дне", example = "4")
    private Integer pointsCount;

    @Schema(description = "Список точек маршрута")
    @JsonProperty("points")
    private List<RoutePointResponse> routePoints;

    @Schema(description = "Дата создания")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private Long routeId;
}
