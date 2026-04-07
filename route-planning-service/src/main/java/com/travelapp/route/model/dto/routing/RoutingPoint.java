package com.travelapp.route.model.dto.routing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingPoint {
    /**
     * Идентификатор точки маршрута (route_points.id). Используется только для ответа наружу.
     */
    private Long routePointId;

    /**
     * Идентификатор исходного POI. Нужен для переиспользования poi_graph_bindings.
     */
    private Long poiId;

    private double latitude;
    private double longitude;
}
