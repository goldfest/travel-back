package com.travelapp.route.service.impl;

import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import com.travelapp.route.repository.PoiGraphBindingRepository;
import com.travelapp.route.repository.RoadNodeRepository;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.PoiSnapService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PoiSnapServiceImpl implements PoiSnapService {

    private final RoadNodeRepository roadNodeRepository;
    private final PoiGraphBindingRepository bindingRepository;
    private final DistanceCalculationService distanceCalculationService;

    @Override
    @Transactional
    public Optional<PoiGraphBinding> snapPoint(Long cityId, RoutingPoint point) {
        if (cityId == null || point == null || point.getRoutePointId() == null) {
            return Optional.empty();
        }

        Optional<PoiGraphBinding> cached = bindingRepository.findById(point.getRoutePointId());
        if (cached.isPresent()) {
            return cached;
        }

        Optional<RoadNode> nearestNode = roadNodeRepository.findNearestNode(cityId, point.getLatitude(), point.getLongitude());
        if (nearestNode.isEmpty()) {
            return Optional.empty();
        }

        RoadNode node = nearestNode.get();
        double snapDistanceKm = distanceCalculationService.calculateDistance(
                new double[]{point.getLatitude(), point.getLongitude()},
                new double[]{node.getLatitude(), node.getLongitude()}
        );

        PoiGraphBinding binding = new PoiGraphBinding();
        binding.setPoiId(point.getRoutePointId());
        binding.setCityId(cityId);
        binding.setNearestNode(node);
        binding.setSnappedLatitude(node.getLatitude());
        binding.setSnappedLongitude(node.getLongitude());
        binding.setSnapDistanceM(snapDistanceKm * 1000.0);
        return Optional.of(bindingRepository.save(binding));
    }

    @Override
    @Transactional
    public Map<Long, PoiGraphBinding> snapPoints(Long cityId, Collection<RoutingPoint> points) {
        Map<Long, PoiGraphBinding> result = new HashMap<>();
        if (points == null || points.isEmpty()) {
            return result;
        }

        List<Long> ids = points.stream()
                .map(RoutingPoint::getRoutePointId)
                .filter(java.util.Objects::nonNull)
                .toList();

        bindingRepository.findByPoiIdIn(ids).forEach(binding -> result.put(binding.getPoiId(), binding));

        for (RoutingPoint point : points) {
            if (point.getRoutePointId() == null || result.containsKey(point.getRoutePointId())) {
                continue;
            }
            snapPoint(cityId, point).ifPresent(binding -> result.put(binding.getPoiId(), binding));
        }

        return result;
    }
}
