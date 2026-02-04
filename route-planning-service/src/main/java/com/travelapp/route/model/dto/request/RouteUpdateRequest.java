package com.travelapp.route.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travelapp.route.model.entity.Route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Запрос на обновление маршрута")
public class RouteUpdateRequest {

    @Size(max = 255, message = "Название маршрута не должно превышать 255 символов")
    @Schema(description = "Название маршрута", example = "Обновленный маршрут по Санкт-Петербургу")
    private String name;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    @Schema(description = "Описание маршрута")
    private String description;

    @Schema(description = "URL обложки маршрута")
    private String coverPhotoUrl;

    @Schema(description = "Режим транспорта")
    private TransportMode transportMode;

    @Schema(description = "Расстояние в километрах")
    private Double distanceKm;

    @Schema(description = "Продолжительность в минутах")
    private Integer durationMin;

    @Schema(description = "Начальная точка")
    private String startPoint;

    @Schema(description = "Конечная точка")
    private String endPoint;

    @Schema(description = "Флаг архивации (0 - активный, 1 - архивный)")
    private Short isArchived;

    @Schema(description = "Дата обновления планирования дня")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dayPlanningDate;

    @Schema(description = "Оптимизирован ли маршрут")
    private Boolean isOptimized;

    @Schema(description = "Режим оптимизации")
    private String optimizationMode;
}