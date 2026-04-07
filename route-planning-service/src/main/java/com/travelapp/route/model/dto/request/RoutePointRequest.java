package com.travelapp.route.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Запрос на добавление точки в маршрут")
public class RoutePointRequest {

    @NotNull(message = "ID POI обязательно")
    @Schema(description = "ID объекта (POI)", example = "123")
    private Long poiId;

    @Schema(description = "Номер дня маршрута (если не указан, добавляется в последний день)", example = "1")
    private Short dayNumber;

    @Schema(description = "Порядковый индекс в дне (если не указан, добавляется в конец)", example = "1")
    private Short orderIndex;

    @Schema(description = "Оценочная продолжительность посещения в минутах", example = "60")
    private Integer estimatedDuration;
}