package com.travelapp.route.service;

import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import com.travelapp.route.model.entity.Route;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PoiSnapService {
    Optional<PoiGraphBinding> snapPoint(Long cityId, RoutingPoint point, Route.TransportMode transportMode);
    Map<Long, PoiGraphBinding> snapPoints(Long cityId, Collection<RoutingPoint> points, Route.TransportMode transportMode);

    @Transactional(readOnly = true)
    Optional<RoadNode> findNearestNode(Long cityId,
                                       double latitude,
                                       double longitude,
                                       Route.TransportMode transportMode);
}
