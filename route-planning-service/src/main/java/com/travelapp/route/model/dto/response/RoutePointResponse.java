package com.travelapp.route.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Ответ с информацией о точке маршрута")
public class RoutePointResponse {

    @Schema(description = "ID точки маршрута", example = "1")
    private Long id;

    @Schema(description = "Порядковый индекс", example = "1")
    private Short orderIndex;

    @Schema(description = "ID объекта (POI)", example = "123")
    private Long poiId;

    @Schema(description = "ID дня маршрута", example = "1")
    private Long routeDayId;

    @Schema(description = "Название объекта", example = "Эрмитаж")
    private String poiName;

    @Schema(description = "Адрес объекта", example = "Дворцовая пл., 2")
    private String poiAddress;

    @Schema(description = "Широта", example = "59.9398")
    private Double poiLatitude;

    @Schema(description = "Долгота", example = "30.3146")
    private Double poiLongitude;

    @Schema(description = "Тип объекта", example = "museum")
    private String poiType;

    @Schema(description = "Оценочная продолжительность посещения в минутах", example = "120")
    private Integer estimatedDuration;

    @Schema(description = "Примерное время прибытия")
    private String estimatedArrivalTime;

    @Schema(description = "Примерное время отбытия")
    private String estimatedDepartureTime;

    @Schema(description = "Дата создания")
    private LocalDateTime createdAt;
}