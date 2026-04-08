package com.travelapp.route.service.impl;

import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import com.travelapp.route.repository.PoiGraphBindingRepository;
import com.travelapp.route.repository.RoadNodeRepository;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.PoiSnapService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final GraphVersionService graphVersionService;
    private final DistanceCalculationService distanceCalculationService;

    @Value("${routing.snap.max-distance-m:250.0}")
    private double maxSnapDistanceM;

    @Override
    @Transactional
    public Optional<PoiGraphBinding> snapPoint(Long cityId, RoutingPoint point) {
        if (cityId == null || point == null) {
            return Optional.empty();
        }

        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);

        if (point.getPoiId() != null) {
            Optional<PoiGraphBinding> cached = bindingRepository.findByPoiIdAndCityId(point.getPoiId(), cityId)
                    .filter(binding -> binding.getGraphVersion() != null)
                    .filter(binding -> graphVersionId.equals(binding.getGraphVersion().getId()))
                    .filter(binding -> binding.getSnapDistanceM() == null || binding.getSnapDistanceM() <= maxSnapDistanceM);

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
        double snapDistanceM = snapDistanceKm * 1000.0;
        if (snapDistanceM > maxSnapDistanceM) {
            return Optional.empty();
        }

        PoiGraphBinding binding = point.getPoiId() == null
                ? new PoiGraphBinding()
                : bindingRepository.findById(point.getPoiId()).orElseGet(PoiGraphBinding::new);
        binding.setPoiId(point.getPoiId());
        binding.setCityId(cityId);
        binding.setGraphVersion(node.getGraphVersion());
        binding.setNearestNode(node);
        binding.setSnappedLatitude(node.getLatitude());
        binding.setSnappedLongitude(node.getLongitude());
        binding.setSnapDistanceM(snapDistanceM);

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

        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);

        List<Long> poiIds = points.stream()
                .map(RoutingPoint::getPoiId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (!poiIds.isEmpty()) {
            bindingRepository.findByPoiIdInAndCityId(poiIds, cityId).stream()
                    .filter(binding -> binding.getGraphVersion() != null && graphVersionId.equals(binding.getGraphVersion().getId()))
                    .filter(binding -> binding.getSnapDistanceM() == null || binding.getSnapDistanceM() <= maxSnapDistanceM)
                    .forEach(binding -> result.put(binding.getPoiId(), binding));
        }

        for (RoutingPoint point : points) {
            Long key = point.getPoiId() != null ? point.getPoiId() : point.getRoutePointId();
            if (key == null || result.containsKey(key)) {
                continue;
            }
            snapPoint(cityId, point).ifPresent(binding -> result.put(key, binding));
        }

        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<RoadNode> findNearestNode(Long cityId, double latitude, double longitude) {
        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);
        return roadNodeRepository.findNearestNode(cityId, graphVersionId, latitude, longitude);
    }
}
