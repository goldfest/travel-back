package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.travelapp.route.model.entity.Route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "Ответ с информацией о маршруте")
public class RouteResponse {

    @Schema(description = "ID маршрута", example = "1")
    private Long id;

    @Schema(description = "Название маршрута", example = "Романтический уикенд")
    private String name;

    @Schema(description = "Описание маршрута")
    private String description;

    @Schema(description = "URL обложки маршрута")
    private String coverPhotoUrl;

    @Schema(description = "Режим транспорта", example = "WALK")
    private TransportMode transportMode;

    @Schema(description = "Оптимизирован ли маршрут", example = "false")
    private Boolean isOptimized;

    @Schema(description = "Режим оптимизации")
    private String optimizationMode;

    @Schema(description = "Расстояние в километрах", example = "15.5")
    private BigDecimal distanceKm;

    @Schema(description = "Продолжительность в минутах", example = "360")
    private Integer durationMin;

    @Schema(description = "Начальная точка")
    private String startPoint;

    @Schema(description = "Конечная точка")
    private String endPoint;

    @Schema(description = "Архивирован ли маршрут", example = "false")
    private Boolean isArchived;

    @Schema(description = "ID пользователя", example = "123")
    private Long userId;

    @Schema(description = "ID города", example = "1")
    private Long cityId;

    @Schema(description = "Название города")
    private String cityName;

    @Schema(description = "Количество дней в маршруте", example = "2")
    private Integer daysCount;

    @Schema(description = "Общее количество точек", example = "8")
    private Integer totalPoints;

    @Schema(description = "Дата создания")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Дата обновления")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @Schema(description = "Список дней маршрута")
    private List<RouteDayResponse> routeDays;

    @Schema(description = "Дополнительные свойства (для расширяемости)")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> additionalProperties = new HashMap<>();

    // Метод для добавления дополнительных свойств
    public void addAdditionalProperty(String key, Object value) {
        if (additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        additionalProperties.put(key, value);
    }

    // Метод для установки дополнительных свойств
    public void setAdditionalProperties(Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    // Метод для получения дополнительных свойств
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}