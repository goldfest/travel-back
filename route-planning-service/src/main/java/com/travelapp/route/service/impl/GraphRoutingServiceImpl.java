package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.*;
import com.travelapp.route.repository.RoadEdgeRepository;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.GraphRoutingService;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.PoiSnapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphRoutingServiceImpl implements GraphRoutingService {

    private static final String PROVIDER = "INTERNAL_GRAPH";
    private static final String SOURCE_GRAPH = "GRAPH";
    private static final String SOURCE_FALLBACK = "FALLBACK";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private final RoadEdgeRepository roadEdgeRepository;
    private final PoiSnapService poiSnapService;
    private final GraphVersionService graphVersionService;
    private final DistanceCalculationService distanceCalculationService;
    private final ObjectMapper objectMapper;

    @Override
    public RoutingSegmentResult buildSegment(Long cityId, RoutingPoint from, RoutingPoint to, Route.TransportMode transportMode) {
        if (from == null || to == null) {
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND);
        }

        if (sameLocation(from, to)) {
            RoutingSegmentResult same = new RoutingSegmentResult();
            same.setFromRoutePointId(from.getRoutePointId());
            same.setToRoutePointId(to.getRoutePointId());
            same.setProvider(PROVIDER);
            same.setGeometrySource(SOURCE_GRAPH);
            same.setStatus(STATUS_OK);
            same.setCoordinates(List.of(new LatLngDto(from.getLatitude(), from.getLongitude())));
            same.setDistanceKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            same.setDurationMin(0);
            return same;
        }

        Optional<PoiGraphBinding> fromBinding = poiSnapService.snapPoint(cityId, from);
        Optional<PoiGraphBinding> toBinding = poiSnapService.snapPoint(cityId, to);
        if (fromBinding.isEmpty() || toBinding.isEmpty()) {
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND);
        }

        RoadGraph graph = loadGraph(cityId, transportMode);
        if (graph.adjacency().isEmpty()) {
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND);
        }

        PathResult path = shortestPath(
                fromBinding.get().getNearestNode(),
                toBinding.get().getNearestNode(),
                graph,
                transportMode
        );

        if (path == null || path.coordinates().isEmpty()) {
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND);
        }

        RoutingSegmentResult result = new RoutingSegmentResult();
        result.setFromRoutePointId(from.getRoutePointId());
        result.setToRoutePointId(to.getRoutePointId());
        result.setProvider(PROVIDER);
        result.setGeometrySource(SOURCE_GRAPH);
        result.setStatus(STATUS_OK);
        result.setCoordinates(path.coordinates());
        result.setDistanceKm(BigDecimal.valueOf(path.distanceKm()).setScale(2, RoundingMode.HALF_UP));
        result.setDurationMin((int) Math.ceil(path.durationSec() / 60.0));
        return result;
    }

    @Override
    public TravelMatrixResult buildMatrix(Long cityId, List<RoutingPoint> points, Route.TransportMode transportMode) {
        TravelMatrixResult result = new TravelMatrixResult();
        if (points == null || points.isEmpty()) {
            result.setDistanceKm(new double[0][0]);
            result.setDurationMin(new int[0][0]);
            return result;
        }

        int n = points.size();
        double[][] distance = new double[n][n];
        int[][] duration = new int[n][n];

        Map<Long, PoiGraphBinding> bindings = poiSnapService.snapPoints(cityId, points);
        RoadGraph graph = loadGraph(cityId, transportMode);

        for (int i = 0; i < n; i++) {
            RoutingPoint originPoint = points.get(i);
            Long originKey = pointKey(originPoint);
            PoiGraphBinding originBinding = bindings.get(originKey);

            if (originBinding == null) {
                fillFallbackRow(points, transportMode, distance, duration, i);
                continue;
            }

            Map<Long, PathMeta> shortest = shortestPathTree(originBinding.getNearestNode(), graph, transportMode);

            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                RoutingPoint targetPoint = points.get(j);
                PoiGraphBinding targetBinding = bindings.get(pointKey(targetPoint));
                if (targetBinding == null) {
                    applyFallback(points.get(i), targetPoint, transportMode, distance, duration, i, j);
                    continue;
                }

                PathMeta meta = shortest.get(targetBinding.getNearestNode().getId());
                if (meta == null) {
                    applyFallback(points.get(i), targetPoint, transportMode, distance, duration, i, j);
                    continue;
                }

                distance[i][j] = meta.distanceKm();
                duration[i][j] = Math.max(1, (int) Math.ceil(meta.durationSec() / 60.0));
            }
        }

        result.setDistanceKm(distance);
        result.setDurationMin(duration);
        return result;
    }

    @Cacheable(cacheNames = "roadGraphByCityAndMode", key = "#cityId + '_' + #transportMode.name()")
    public RoadGraph loadGraph(Long cityId, Route.TransportMode transportMode) {
        CityGraphVersion activeVersion = graphVersionService.getActiveVersionOrThrow(cityId);

        List<RoadEdge> edges = roadEdgeRepository.findByGraphVersion_Id(activeVersion.getId());
        Map<Long, List<EdgeState>> adjacency = new HashMap<>();

        for (RoadEdge edge : edges) {
            if (!isAllowed(edge, transportMode)) {
                continue;
            }

            List<LatLngDto> coords = readCoordinates(edge.getPolylineJson(), edge.getFromNode(), edge.getToNode());
            double distanceKm = edge.getLengthM() == null ? estimateDistance(coords) : edge.getLengthM() / 1000.0;
            int durationSec = resolveDurationSec(edge, transportMode, distanceKm);

            adjacency.computeIfAbsent(edge.getFromNode().getId(), k -> new ArrayList<>())
                    .add(new EdgeState(edge.getToNode(), distanceKm, durationSec, coords));

            if (Boolean.TRUE.equals(edge.getBidirectional())) {
                adjacency.computeIfAbsent(edge.getToNode().getId(), k -> new ArrayList<>())
                        .add(new EdgeState(edge.getFromNode(), distanceKm, durationSec, reverse(coords)));
            }
        }

        return new RoadGraph(cityId, transportMode, adjacency);
    }

    private PathResult shortestPath(RoadNode start, RoadNode goal, RoadGraph graph, Route.TransportMode mode) {
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, StateRef> prev = new HashMap<>();
        PriorityQueue<NodeState> open = new PriorityQueue<>(Comparator.comparingDouble(NodeState::fScore));
        Set<Long> closed = new HashSet<>();

        gScore.put(start.getId(), 0.0);
        open.add(new NodeState(start, heuristic(start, goal, mode), 0.0));

        while (!open.isEmpty()) {
            NodeState current = open.poll();
            if (!closed.add(current.node().getId())) {
                continue;
            }

            if (current.node().getId().equals(goal.getId())) {
                return reconstruct(goal, prev, gScore.get(goal.getId()));
            }

            for (EdgeState edge : graph.adjacency().getOrDefault(current.node().getId(), List.of())) {
                if (closed.contains(edge.to().getId())) {
                    continue;
                }
                double tentative = gScore.getOrDefault(current.node().getId(), Double.POSITIVE_INFINITY) + edge.durationSec();
                if (tentative < gScore.getOrDefault(edge.to().getId(), Double.POSITIVE_INFINITY)) {
                    gScore.put(edge.to().getId(), tentative);
                    prev.put(edge.to().getId(), new StateRef(current.node(), edge));
                    open.add(new NodeState(edge.to(), tentative + heuristic(edge.to(), goal, mode), tentative));
                }
            }
        }

        return null;
    }

    private Map<Long, PathMeta> shortestPathTree(RoadNode start, RoadGraph graph, Route.TransportMode mode) {
        Map<Long, Double> duration = new HashMap<>();
        Map<Long, Double> distance = new HashMap<>();
        PriorityQueue<NodeState> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeState::gScore));

        duration.put(start.getId(), 0.0);
        distance.put(start.getId(), 0.0);
        queue.add(new NodeState(start, 0.0, 0.0));

        while (!queue.isEmpty()) {
            NodeState current = queue.poll();
            double known = duration.getOrDefault(current.node().getId(), Double.POSITIVE_INFINITY);
            if (current.gScore() > known) {
                continue;
            }

            for (EdgeState edge : graph.adjacency().getOrDefault(current.node().getId(), List.of())) {
                double candidateDuration = current.gScore() + edge.durationSec();
                if (candidateDuration < duration.getOrDefault(edge.to().getId(), Double.POSITIVE_INFINITY)) {
                    duration.put(edge.to().getId(), candidateDuration);
                    distance.put(edge.to().getId(), distance.getOrDefault(current.node().getId(), 0.0) + edge.distanceKm());
                    queue.add(new NodeState(edge.to(), candidateDuration, candidateDuration));
                }
            }
        }

        Map<Long, PathMeta> result = new HashMap<>();
        duration.forEach((nodeId, durationSec) -> result.put(nodeId,
                new PathMeta(distance.getOrDefault(nodeId, 0.0), (int) Math.round(durationSec))));
        return result;
    }

    private PathResult reconstruct(RoadNode goal, Map<Long, StateRef> prev, double totalDurationSec) {
        List<List<LatLngDto>> chunks = new ArrayList<>();
        double totalDistance = 0.0;
        RoadNode cursor = goal;

        while (prev.containsKey(cursor.getId())) {
            StateRef ref = prev.get(cursor.getId());
            chunks.add(ref.edge().coordinates());
            totalDistance += ref.edge().distanceKm();
            cursor = ref.previousNode();
        }

        Collections.reverse(chunks);
        List<LatLngDto> merged = new ArrayList<>();
        for (List<LatLngDto> chunk : chunks) {
            mergeCoordinates(merged, chunk);
        }
        return new PathResult(merged, totalDistance, (int) Math.round(totalDurationSec));
    }

    private void fillFallbackRow(List<RoutingPoint> points,
                                 Route.TransportMode transportMode,
                                 double[][] distance,
                                 int[][] duration,
                                 int row) {
        for (int j = 0; j < points.size(); j++) {
            if (row == j) {
                continue;
            }
            applyFallback(points.get(row), points.get(j), transportMode, distance, duration, row, j);
        }
    }

    private void applyFallback(RoutingPoint from,
                               RoutingPoint to,
                               Route.TransportMode transportMode,
                               double[][] distance,
                               int[][] duration,
                               int i,
                               int j) {
        RoutingSegmentResult seg = fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND);
        distance[i][j] = seg.getDistanceKm() == null ? 0.0 : seg.getDistanceKm().doubleValue();
        duration[i][j] = seg.getDurationMin() == null ? 0 : seg.getDurationMin();
    }

    private boolean sameLocation(RoutingPoint from, RoutingPoint to) {
        return Double.compare(from.getLatitude(), to.getLatitude()) == 0
                && Double.compare(from.getLongitude(), to.getLongitude()) == 0;
    }

    private Long pointKey(RoutingPoint point) {
        return point.getPoiId() != null ? point.getPoiId() : point.getRoutePointId();
    }

    private double heuristic(RoadNode from, RoadNode to, Route.TransportMode mode) {
        double km = distanceCalculationService.calculateDistance(
                new double[]{from.getLatitude(), from.getLongitude()},
                new double[]{to.getLatitude(), to.getLongitude()}
        );
        return Math.max(distanceCalculationService.calculateTravelTime(km, mode.name()) * 60 * 0.65, 1.0);
    }

    private RoutingSegmentResult fallbackSegment(RoutingPoint from, RoutingPoint to, Route.TransportMode transportMode, String status) {
        RoutingSegmentResult segment = new RoutingSegmentResult();
        if (from != null) {
            segment.setFromRoutePointId(from.getRoutePointId());
        }
        if (to != null) {
            segment.setToRoutePointId(to.getRoutePointId());
        }
        segment.setProvider(PROVIDER);
        segment.setGeometrySource(SOURCE_FALLBACK);
        segment.setStatus(status);

        if (from == null || to == null) {
            segment.setCoordinates(List.of());
            segment.setDistanceKm(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            segment.setDurationMin(0);
            return segment;
        }

        double distanceKm = distanceCalculationService.calculateDistance(
                new double[]{from.getLatitude(), from.getLongitude()},
                new double[]{to.getLatitude(), to.getLongitude()}
        );
        int durationMin = distanceCalculationService.calculateTravelTime(distanceKm, transportMode.name());

        segment.setCoordinates(List.of(
                new LatLngDto(from.getLatitude(), from.getLongitude()),
                new LatLngDto(to.getLatitude(), to.getLongitude())
        ));
        segment.setDistanceKm(BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP));
        segment.setDurationMin(durationMin);
        return segment;
    }

    private boolean isAllowed(RoadEdge edge, Route.TransportMode mode) {
        return switch (mode) {
            case WALK -> Boolean.TRUE.equals(edge.getWalkAllowed());
            case CAR -> Boolean.TRUE.equals(edge.getCarAllowed());
            case MIXED -> Boolean.TRUE.equals(edge.getMixedAllowed());
            case PUBLIC_TRANSPORT -> Boolean.TRUE.equals(edge.getPublicTransportAllowed());
        };
    }

    private int resolveDurationSec(RoadEdge edge, Route.TransportMode mode, double distanceKm) {
        Integer value = switch (mode) {
            case WALK -> edge.getWalkTimeSec();
            case CAR -> edge.getCarTimeSec();
            case MIXED -> edge.getMixedTimeSec();
            case PUBLIC_TRANSPORT -> edge.getPublicTransportTimeSec();
        };
        if (value != null && value > 0) {
            return value;
        }
        return Math.max(distanceCalculationService.calculateTravelTime(distanceKm, mode.name()) * 60, 1);
    }

    private List<LatLngDto> readCoordinates(String json, RoadNode fromNode, RoadNode toNode) {
        try {
            if (json != null && !json.isBlank() && !"[]".equals(json.trim())) {
                return objectMapper.readValue(json, new TypeReference<List<LatLngDto>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to parse road edge polyline_json", e);
        }
        return List.of(
                new LatLngDto(fromNode.getLatitude(), fromNode.getLongitude()),
                new LatLngDto(toNode.getLatitude(), toNode.getLongitude())
        );
    }

    private double estimateDistance(List<LatLngDto> coordinates) {
        double total = 0.0;
        for (int i = 1; i < coordinates.size(); i++) {
            total += distanceCalculationService.calculateDistance(
                    new double[]{coordinates.get(i - 1).getLatitude(), coordinates.get(i - 1).getLongitude()},
                    new double[]{coordinates.get(i).getLatitude(), coordinates.get(i).getLongitude()}
            );
        }
        return total;
    }

    private List<LatLngDto> reverse(List<LatLngDto> coordinates) {
        List<LatLngDto> reversed = new ArrayList<>(coordinates);
        Collections.reverse(reversed);
        return reversed;
    }

    private void mergeCoordinates(List<LatLngDto> merged, List<LatLngDto> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }
        if (merged.isEmpty()) {
            merged.addAll(candidate);
            return;
        }

        LatLngDto last = merged.get(merged.size() - 1);
        LatLngDto first = candidate.get(0);
        int startIndex = (Double.compare(last.getLatitude(), first.getLatitude()) == 0
                && Double.compare(last.getLongitude(), first.getLongitude()) == 0) ? 1 : 0;
        for (int i = startIndex; i < candidate.size(); i++) {
            merged.add(candidate.get(i));
        }
    }

    public record RoadGraph(Long cityId, Route.TransportMode transportMode, Map<Long, List<EdgeState>> adjacency) {}
    private record NodeState(RoadNode node, double fScore, double gScore) {}
    private record StateRef(RoadNode previousNode, EdgeState edge) {}
    public record EdgeState(RoadNode to, double distanceKm, int durationSec, List<LatLngDto> coordinates) {}
    private record PathMeta(double distanceKm, int durationSec) {}
    private record PathResult(List<LatLngDto> coordinates, double distanceKm, int durationSec) {}
}
