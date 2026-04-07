package com.travelapp.route.service;

import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface PoiSnapService {
    Optional<PoiGraphBinding> snapPoint(Long cityId, RoutingPoint point);
    Map<Long, PoiGraphBinding> snapPoints(Long cityId, Collection<RoutingPoint> points);

    @Transactional(readOnly = true)
    Optional<RoadNode> findNearestNode(Long cityId, double latitude, double longitude);
}
