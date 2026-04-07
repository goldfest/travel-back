package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelapp.route.model.entity.Route;
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

    private Long id;
    private String name;
    private String description;
    private String coverPhotoUrl;
    private TransportMode transportMode;
    private Boolean isOptimized;
    private String optimizationMode;
    private BigDecimal distanceKm;
    private Integer durationMin;
    private String startPoint;
    private String endPoint;
    private Boolean isArchived;
    private Long userId;
    private Long cityId;
    private String cityName;
    private Integer daysCount;
    private Integer totalPoints;
    private List<String> warnings;
    private Route.RouteStatus status;

    private Long fromRoutePointId;
    private Long toRoutePointId;

    private RoutePolylineDto polyline;
    private String provider;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonProperty("days")
    private List<RouteDayResponse> routeDays;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> additionalProperties = new HashMap<>();

    public void addAdditionalProperty(String key, Object value) {
        if (additionalProperties == null) {
            additionalProperties = new HashMap<>();
        }
        additionalProperties.put(key, value);
    }
}
