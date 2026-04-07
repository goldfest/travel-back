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
import java.util.Objects;
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
        if (cityId == null || point == null) {
            return Optional.empty();
        }

        // Стабильный кэш используем только для реального POI, а не для route_point.
        if (point.getPoiId() != null) {
            Optional<PoiGraphBinding> cached = bindingRepository.findById(point.getPoiId())
                    .filter(binding -> Objects.equals(binding.getCityId(), cityId));
            if (cached.isPresent()) {
                return cached;
            }
        }

        Optional<RoadNode> nearestNode = findNearestNode(cityId, point.getLatitude(), point.getLongitude());
        if (nearestNode.isEmpty()) {
            return Optional.empty();
        }

        RoadNode node = nearestNode.get();
        double snapDistanceKm = distanceCalculationService.calculateDistance(
                new double[]{point.getLatitude(), point.getLongitude()},
                new double[]{node.getLatitude(), node.getLongitude()}
        );

        PoiGraphBinding binding = new PoiGraphBinding();
        binding.setPoiId(point.getPoiId());
        binding.setCityId(cityId);
        binding.setNearestNode(node);
        binding.setSnappedLatitude(node.getLatitude());
        binding.setSnappedLongitude(node.getLongitude());
        binding.setSnapDistanceM(snapDistanceKm * 1000.0);

        // Временную точку маршрута не сохраняем в таблицу poi_graph_bindings,
        // иначе route_points.id начнёт загрязнять постоянный кэш по POI.
        if (point.getPoiId() == null) {
            return Optional.of(binding);
        }

        return Optional.of(bindingRepository.save(binding));
    }

    @Override
    @Transactional
    public Map<Long, PoiGraphBinding> snapPoints(Long cityId, Collection<RoutingPoint> points) {
        Map<Long, PoiGraphBinding> result = new HashMap<>();
        if (points == null || points.isEmpty()) {
            return result;
        }

        List<Long> poiIds = points.stream()
                .map(RoutingPoint::getPoiId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        bindingRepository.findByPoiIdIn(poiIds).stream()
                .filter(binding -> Objects.equals(binding.getCityId(), cityId))
                .forEach(binding -> result.put(binding.getPoiId(), binding));

        for (RoutingPoint point : points) {
            Long key = point.getPoiId() != null ? point.getPoiId() : point.getRoutePointId();
            if (key == null || result.containsKey(key)) {
                continue;
            }
            snapPoint(cityId, point).ifPresent(binding -> result.put(key, binding));
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RoadNode> findNearestNode(Long cityId, double latitude, double longitude) {
        return roadNodeRepository.findNearestNode(cityId, latitude, longitude);
    }
}
