package com.travelapp.route.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travelapp.route.model.entity.Route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Запрос на создание маршрута")
public class RouteCreateRequest {

    @NotBlank(message = "Название маршрута обязательно")
    @Size(max = 255, message = "Название маршрута не должно превышать 255 символов")
    @Schema(description = "Название маршрута", example = "Романтический уикенд в Санкт-Петербурге")
    private String name;

    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    @Schema(description = "Описание маршрута", example = "Уикенд для влюбленных с посещением лучших мест")
    private String description;

    @NotNull(message = "ID города обязателен")
    @Schema(description = "ID города", example = "1")
    private Long cityId;

    @Schema(description = "Режим транспорта", example = "WALK")
    private TransportMode transportMode = TransportMode.WALK;

    @Schema(description = "Дата начала маршрута")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startDate;

    @Schema(description = "Количество дней в маршруте", example = "2")
    private Integer daysCount = 1;
}