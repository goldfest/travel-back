package com.travelapp.route.service.impl;

import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import com.travelapp.route.model.entity.Route;
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

    @Value("${routing.snap.max-distance-car-m:1000.0}")
    private double maxSnapDistanceCarM;

    @Override
    @Transactional
    public Optional<PoiGraphBinding> snapPoint(Long cityId, RoutingPoint point, Route.TransportMode transportMode) {
        if (cityId == null || point == null) {
            return Optional.empty();
        }

        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);

        if (point.getPoiId() != null) {
            Optional<PoiGraphBinding> cached = bindingRepository.findByPoiIdAndCityId(point.getPoiId(), cityId)
                    .filter(binding -> binding.getGraphVersion() != null)
                    .filter(binding -> graphVersionId.equals(binding.getGraphVersion().getId()))
                    .filter(binding -> binding.getSnapDistanceM() == null || binding.getSnapDistanceM() <= resolveMaxSnapDistanceM(transportMode))
                    .filter(binding -> isBindingUsableForMode(binding, transportMode));

            if (cached.isPresent()) {
                return cached;
            }
        }

        Optional<RoadNode> nearestNode = findNearestNode(cityId, point.getLatitude(), point.getLongitude(), transportMode);
        if (nearestNode.isEmpty()) {
            return Optional.empty();
        }

        RoadNode node = nearestNode.get();
        double snapDistanceKm = distanceCalculationService.calculateDistance(
                new double[]{point.getLatitude(), point.getLongitude()},
                new double[]{node.getLatitude(), node.getLongitude()}
        );
        double snapDistanceM = snapDistanceKm * 1000.0;
        if (snapDistanceM > resolveMaxSnapDistanceM(transportMode)) {
            return Optional.empty();
        }

        PoiGraphBinding binding = point.getPoiId() == null
                ? new PoiGraphBinding()
                : bindingRepository.findByPoiIdAndCityId(point.getPoiId(), cityId).orElseGet(PoiGraphBinding::new);
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
    public Map<Long, PoiGraphBinding> snapPoints(Long cityId, Collection<RoutingPoint> points, Route.TransportMode transportMode) {
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
                    .filter(binding -> binding.getSnapDistanceM() == null || binding.getSnapDistanceM() <= resolveMaxSnapDistanceM(transportMode))
                    .filter(binding -> isBindingUsableForMode(binding, transportMode))
                    .forEach(binding -> result.put(binding.getPoiId(), binding));
        }

        for (RoutingPoint point : points) {
            Long key = point.getPoiId() != null ? point.getPoiId() : point.getRoutePointId();
            if (key == null || result.containsKey(key)) {
                continue;
            }
            snapPoint(cityId, point, transportMode).ifPresent(binding -> result.put(key, binding));
        }

        return result;
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<RoadNode> findNearestNode(Long cityId,
                                              double latitude,
                                              double longitude,
                                              Route.TransportMode transportMode) {
        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);
        if (transportMode == Route.TransportMode.CAR) {
            return roadNodeRepository.findNearestCarNode(cityId, graphVersionId, latitude, longitude);
        }
        return roadNodeRepository.findNearestNode(cityId, graphVersionId, latitude, longitude);
    }

    private double resolveMaxSnapDistanceM(Route.TransportMode transportMode) {
        return transportMode == Route.TransportMode.CAR ? maxSnapDistanceCarM : maxSnapDistanceM;
    }

    private boolean isBindingUsableForMode(PoiGraphBinding binding, Route.TransportMode transportMode) {
        if (binding == null || binding.getNearestNode() == null) {
            return false;
        }

        Long cityId = binding.getCityId();
        Long graphVersionId = binding.getGraphVersion() != null ? binding.getGraphVersion().getId() : null;
        Double latitude = binding.getNearestNode().getLatitude();
        Double longitude = binding.getNearestNode().getLongitude();
        Long nearestNodeId = binding.getNearestNode().getId();

        if (cityId == null || graphVersionId == null || latitude == null || longitude == null || nearestNodeId == null) {
            return false;
        }

        if (transportMode != Route.TransportMode.CAR) {
            return true;
        }

        return roadNodeRepository.findNearestCarNode(cityId, graphVersionId, latitude, longitude)
                .map(RoadNode::getId)
                .filter(nearestNodeId::equals)
                .isPresent();
    }
}
