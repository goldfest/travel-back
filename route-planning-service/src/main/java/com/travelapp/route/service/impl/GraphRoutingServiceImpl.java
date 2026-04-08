package com.travelapp.route.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.route.model.dto.response.LatLngDto;
import com.travelapp.route.model.dto.routing.RoutingPoint;
import com.travelapp.route.model.dto.routing.RoutingSegmentResult;
import com.travelapp.route.model.dto.routing.TravelMatrixResult;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.model.entity.PoiGraphBinding;
import com.travelapp.route.model.entity.RoadNode;
import com.travelapp.route.model.entity.Route;
import com.travelapp.route.repository.RoadEdgeProjection;
import com.travelapp.route.repository.RoadEdgeRepository;
import com.travelapp.route.service.DistanceCalculationService;
import com.travelapp.route.service.GraphRoutingService;
import com.travelapp.route.service.GraphVersionService;
import com.travelapp.route.service.PoiSnapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphRoutingServiceImpl implements GraphRoutingService {

    private static final String PROVIDER = "INTERNAL_GRAPH";
    private static final String SOURCE_GRAPH = "GRAPH";
    private static final String SOURCE_FALLBACK = "FALLBACK";
    private static final String STATUS_OK = "OK";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private static final String REASON_NO_ACTIVE_GRAPH = "NO_ACTIVE_GRAPH_VERSION";
    private static final String REASON_POINT_NOT_SNAPPED = "POINT_NOT_SNAPPED";
    private static final String REASON_GRAPH_EMPTY = "GRAPH_EMPTY";
    private static final String REASON_PATH_NOT_FOUND = "PATH_NOT_FOUND";

    private final RoadEdgeRepository roadEdgeRepository;
    private final PoiSnapService poiSnapService;
    private final GraphVersionService graphVersionService;
    private final DistanceCalculationService distanceCalculationService;
    private final ObjectMapper objectMapper;

    @Override
    public RoutingSegmentResult buildSegment(Long cityId, RoutingPoint from, RoutingPoint to, Route.TransportMode transportMode) {
        if (from == null || to == null) {
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, REASON_POINT_NOT_SNAPPED, null);
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

        CityGraphVersion activeVersion;
        try {
            activeVersion = graphVersionService.getActiveVersionOrThrow(cityId);
        } catch (Exception ex) {
            log.warn("Fallback: no active graph version for cityId={}", cityId, ex);
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, REASON_NO_ACTIVE_GRAPH, null);
        }

        Optional<PoiGraphBinding> fromBinding = poiSnapService.snapPoint(cityId, from);
        Optional<PoiGraphBinding> toBinding = poiSnapService.snapPoint(cityId, to);
        if (fromBinding.isEmpty() || toBinding.isEmpty()) {
            log.debug("Fallback: point not snapped, cityId={}, fromPoiId={}, toPoiId={}", cityId, from.getPoiId(), to.getPoiId());
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, REASON_POINT_NOT_SNAPPED, activeVersion.getId());
        }

        RoadGraph graph = loadGraphByVersion(cityId, activeVersion.getId(), transportMode);
        if (graph.adjacency().isEmpty()) {
            log.warn("Fallback: graph is empty, cityId={}, graphVersionId={}, mode={}", cityId, activeVersion.getId(), transportMode);
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, REASON_GRAPH_EMPTY, activeVersion.getId());
        }

        NodeRef start = toNodeRef(fromBinding.get().getNearestNode());
        NodeRef goal = toNodeRef(toBinding.get().getNearestNode());
        PathResult path = shortestPath(start, goal, graph, transportMode);

        if (path == null || path.coordinates().isEmpty()) {
            log.debug("Fallback: path not found, cityId={}, graphVersionId={}, fromNode={}, toNode={}, mode={}",
                    cityId, activeVersion.getId(), start.id(), goal.id(), transportMode);
            return fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, REASON_PATH_NOT_FOUND, activeVersion.getId());
        }

        RoutingSegmentResult result = new RoutingSegmentResult();
        result.setFromRoutePointId(from.getRoutePointId());
        result.setToRoutePointId(to.getRoutePointId());
        result.setProvider(PROVIDER);
        result.setGeometrySource(SOURCE_GRAPH);
        result.setStatus(STATUS_OK);
        result.setDebugReason("GRAPH_OK");
        result.setGraphVersionId(activeVersion.getId());
        result.setCoordinates(path.coordinates());
        result.setDistanceKm(BigDecimal.valueOf(path.distanceKm()).setScale(2, RoundingMode.HALF_UP));
        result.setDurationMin((int) Math.ceil(path.durationSec() / 60.0d));
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
        RoadGraph graph;
        try {
            graph = loadGraph(cityId, transportMode);
        } catch (Exception ex) {
            for (int i = 0; i < n; i++) {
                fillFallbackRow(points, transportMode, distance, duration, i, REASON_NO_ACTIVE_GRAPH);
            }
            result.setDistanceKm(distance);
            result.setDurationMin(duration);
            return result;
        }

        for (int i = 0; i < n; i++) {
            RoutingPoint originPoint = points.get(i);
            PoiGraphBinding originBinding = bindings.get(pointKey(originPoint));
            if (originBinding == null) {
                fillFallbackRow(points, transportMode, distance, duration, i, REASON_POINT_NOT_SNAPPED);
                continue;
            }

            Map<Long, PathMeta> shortest = shortestPathTree(toNodeRef(originBinding.getNearestNode()), graph, transportMode);
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }

                RoutingPoint targetPoint = points.get(j);
                PoiGraphBinding targetBinding = bindings.get(pointKey(targetPoint));
                if (targetBinding == null) {
                    applyFallback(points.get(i), targetPoint, transportMode, distance, duration, i, j, REASON_POINT_NOT_SNAPPED);
                    continue;
                }

                PathMeta meta = shortest.get(targetBinding.getNearestNode().getId());
                if (meta == null) {
                    applyFallback(points.get(i), targetPoint, transportMode, distance, duration, i, j, REASON_PATH_NOT_FOUND);
                    continue;
                }

                distance[i][j] = meta.distanceKm();
                duration[i][j] = Math.max(1, (int) Math.ceil(meta.durationSec() / 60.0d));
            }
        }

        result.setDistanceKm(distance);
        result.setDurationMin(duration);
        return result;
    }

    public RoadGraph loadGraph(Long cityId, Route.TransportMode transportMode) {
        Long graphVersionId = graphVersionService.getRequiredActiveVersionId(cityId);
        return loadGraphByVersion(cityId, graphVersionId, transportMode);
    }

    @Cacheable(cacheNames = "roadGraphByCityAndMode", key = "#cityId + '_' + #graphVersionId + '_' + #transportMode.name()")
    public RoadGraph loadGraphByVersion(Long cityId, Long graphVersionId, Route.TransportMode transportMode) {
        long startedAt = System.currentTimeMillis();
        List<RoadEdgeProjection> edges = roadEdgeRepository.findProjectedByGraphVersionId(graphVersionId);
        Map<Long, List<EdgeState>> adjacency = new HashMap<>(Math.max(16, edges.size() / 2));

        int keptEdges = 0;
        for (RoadEdgeProjection edge : edges) {
            if (!isAllowed(edge, transportMode)) {
                continue;
            }

            NodeRef from = new NodeRef(edge.getFromNodeId(), edge.getFromLatitude(), edge.getFromLongitude());
            NodeRef to = new NodeRef(edge.getToNodeId(), edge.getToLatitude(), edge.getToLongitude());
            List<LatLngDto> coords = readCoordinates(edge.getPolylineJson(), from, to);
            double distanceKm = edge.getLengthM() == null ? estimateDistance(coords) : edge.getLengthM() / 1000.0d;
            int durationSec = resolveDurationSec(edge, transportMode, distanceKm);

            adjacency.computeIfAbsent(from.id(), ignored -> new ArrayList<>())
                    .add(new EdgeState(to, distanceKm, durationSec, coords));
            keptEdges++;

            if (Boolean.TRUE.equals(edge.getBidirectional())) {
                adjacency.computeIfAbsent(to.id(), ignored -> new ArrayList<>())
                        .add(new EdgeState(from, distanceKm, durationSec, reverse(coords)));
            }
        }

        log.info("Road graph loaded: cityId={}, graphVersionId={}, mode={}, rawEdges={}, keptEdges={}, adjacencyNodes={}, loadMs={}",
                cityId, graphVersionId, transportMode, edges.size(), keptEdges, adjacency.size(), System.currentTimeMillis() - startedAt);
        return new RoadGraph(cityId, transportMode, adjacency);
    }

    @CacheEvict(cacheNames = "roadGraphByCityAndMode", allEntries = true)
    public void evictRoadGraphCache() {
        log.info("Evicted roadGraphByCityAndMode cache");
    }

    private PathResult shortestPath(NodeRef start, NodeRef goal, RoadGraph graph, Route.TransportMode mode) {
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, StateRef> prev = new HashMap<>();
        PriorityQueue<NodeState> open = new PriorityQueue<>(Comparator.comparingDouble(NodeState::fScore));
        Set<Long> closed = new HashSet<>();

        gScore.put(start.id(), 0.0d);
        open.add(new NodeState(start, heuristic(start, goal, mode), 0.0d));

        while (!open.isEmpty()) {
            NodeState current = open.poll();
            if (!closed.add(current.node().id())) {
                continue;
            }

            if (current.node().id().equals(goal.id())) {
                return reconstruct(goal.id(), prev, gScore.get(goal.id()));
            }

            for (EdgeState edge : graph.adjacency().getOrDefault(current.node().id(), List.of())) {
                if (closed.contains(edge.to().id())) {
                    continue;
                }
                double tentative = gScore.getOrDefault(current.node().id(), Double.POSITIVE_INFINITY) + edge.durationSec();
                if (tentative < gScore.getOrDefault(edge.to().id(), Double.POSITIVE_INFINITY)) {
                    gScore.put(edge.to().id(), tentative);
                    prev.put(edge.to().id(), new StateRef(current.node(), edge));
                    open.add(new NodeState(edge.to(), tentative + heuristic(edge.to(), goal, mode), tentative));
                }
            }
        }

        return null;
    }

    private Map<Long, PathMeta> shortestPathTree(NodeRef start, RoadGraph graph, Route.TransportMode mode) {
        Map<Long, Double> duration = new HashMap<>();
        Map<Long, Double> distance = new HashMap<>();
        PriorityQueue<NodeState> queue = new PriorityQueue<>(Comparator.comparingDouble(NodeState::gScore));

        duration.put(start.id(), 0.0d);
        distance.put(start.id(), 0.0d);
        queue.add(new NodeState(start, 0.0d, 0.0d));

        while (!queue.isEmpty()) {
            NodeState current = queue.poll();
            double known = duration.getOrDefault(current.node().id(), Double.POSITIVE_INFINITY);
            if (current.gScore() > known) {
                continue;
            }

            for (EdgeState edge : graph.adjacency().getOrDefault(current.node().id(), List.of())) {
                double candidateDuration = current.gScore() + edge.durationSec();
                if (candidateDuration < duration.getOrDefault(edge.to().id(), Double.POSITIVE_INFINITY)) {
                    duration.put(edge.to().id(), candidateDuration);
                    distance.put(edge.to().id(), distance.getOrDefault(current.node().id(), 0.0d) + edge.distanceKm());
                    queue.add(new NodeState(edge.to(), candidateDuration, candidateDuration));
                }
            }
        }

        Map<Long, PathMeta> result = new HashMap<>();
        duration.forEach((nodeId, durationSec) -> result.put(nodeId,
                new PathMeta(distance.getOrDefault(nodeId, 0.0d), (int) Math.round(durationSec))));
        return result;
    }

    private PathResult reconstruct(Long goalNodeId, Map<Long, StateRef> prev, double totalDurationSec) {
        List<List<LatLngDto>> chunks = new ArrayList<>();
        double totalDistance = 0.0d;
        Long cursor = goalNodeId;

        while (prev.containsKey(cursor)) {
            StateRef ref = prev.get(cursor);
            chunks.add(ref.edge().coordinates());
            totalDistance += ref.edge().distanceKm();
            cursor = ref.previousNode().id();
        }

        java.util.Collections.reverse(chunks);
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
                                 int row,
                                 String reason) {
        for (int j = 0; j < points.size(); j++) {
            if (row == j) {
                continue;
            }
            applyFallback(points.get(row), points.get(j), transportMode, distance, duration, row, j, reason);
        }
    }

    private void applyFallback(RoutingPoint from,
                               RoutingPoint to,
                               Route.TransportMode transportMode,
                               double[][] distance,
                               int[][] duration,
                               int i,
                               int j,
                               String reason) {
        RoutingSegmentResult seg = fallbackSegment(from, to, transportMode, STATUS_NOT_FOUND, reason, null);
        distance[i][j] = seg.getDistanceKm() == null ? 0.0d : seg.getDistanceKm().doubleValue();
        duration[i][j] = seg.getDurationMin() == null ? 0 : seg.getDurationMin();
    }

    private boolean sameLocation(RoutingPoint from, RoutingPoint to) {
        return Double.compare(from.getLatitude(), to.getLatitude()) == 0
                && Double.compare(from.getLongitude(), to.getLongitude()) == 0;
    }

    private Long pointKey(RoutingPoint point) {
        return point.getPoiId() != null ? point.getPoiId() : point.getRoutePointId();
    }

    private double heuristic(NodeRef from, NodeRef to, Route.TransportMode mode) {
        double km = distanceCalculationService.calculateDistance(
                new double[]{from.latitude(), from.longitude()},
                new double[]{to.latitude(), to.longitude()}
        );
        return Math.max(distanceCalculationService.calculateTravelTime(km, mode.name()) * 60.0d * 0.65d, 1.0d);
    }

    private RoutingSegmentResult fallbackSegment(RoutingPoint from,
                                                 RoutingPoint to,
                                                 Route.TransportMode transportMode,
                                                 String status,
                                                 String reason,
                                                 Long graphVersionId) {
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
        segment.setDebugReason(reason);
        segment.setGraphVersionId(graphVersionId);

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

    private boolean isAllowed(RoadEdgeProjection edge, Route.TransportMode mode) {
        return switch (mode) {
            case WALK -> Boolean.TRUE.equals(edge.getWalkAllowed());
            case CAR -> Boolean.TRUE.equals(edge.getCarAllowed());
            case MIXED -> Boolean.TRUE.equals(edge.getMixedAllowed());
            case PUBLIC_TRANSPORT -> Boolean.TRUE.equals(edge.getPublicTransportAllowed());
        };
    }

    private int resolveDurationSec(RoadEdgeProjection edge, Route.TransportMode mode, double distanceKm) {
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

    private List<LatLngDto> readCoordinates(String json, NodeRef fromNode, NodeRef toNode) {
        try {
            if (json != null && !json.isBlank() && !"[]".equals(json.trim())) {
                List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
                });
                List<LatLngDto> result = new ArrayList<>();

                for (Map<String, Object> point : raw) {
                    if (point == null || point.isEmpty()) {
                        continue;
                    }

                    Double latitude = toDouble(point.get("latitude"));
                    Double longitude = toDouble(point.get("longitude"));

                    if (latitude == null) {
                        latitude = toDouble(point.get("lat"));
                    }
                    if (longitude == null) {
                        longitude = toDouble(point.get("lng"));
                    }
                    if (longitude == null) {
                        longitude = toDouble(point.get("lon"));
                    }

                    if (latitude != null && longitude != null) {
                        result.add(new LatLngDto(latitude, longitude));
                    }
                }

                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse road edge polyline_json", e);
        }
        return List.of(
                new LatLngDto(fromNode.latitude(), fromNode.longitude()),
                new LatLngDto(toNode.latitude(), toNode.longitude())
        );
    }

    private double estimateDistance(List<LatLngDto> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return 0.0d;
        }
        double total = 0.0d;
        for (int i = 1; i < coordinates.size(); i++) {
            LatLngDto a = coordinates.get(i - 1);
            LatLngDto b = coordinates.get(i);
            total += distanceCalculationService.calculateDistance(
                    new double[]{a.getLatitude(), a.getLongitude()},
                    new double[]{b.getLatitude(), b.getLongitude()}
            );
        }
        return total;
    }

    private List<LatLngDto> reverse(List<LatLngDto> coordinates) {
        List<LatLngDto> reversed = new ArrayList<>(coordinates);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private void mergeCoordinates(List<LatLngDto> merged, List<LatLngDto> candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return;
        }

        List<LatLngDto> safeCandidate = candidate.stream()
                .filter(Objects::nonNull)
                .filter(point -> point.getLatitude() != null && point.getLongitude() != null)
                .toList();

        if (safeCandidate.isEmpty()) {
            return;
        }

        if (merged.isEmpty()) {
            merged.addAll(safeCandidate);
            return;
        }

        LatLngDto last = merged.get(merged.size() - 1);
        LatLngDto first = safeCandidate.get(0);
        boolean samePoint = last != null
                && first != null
                && last.getLatitude() != null
                && last.getLongitude() != null
                && first.getLatitude() != null
                && first.getLongitude() != null
                && Double.compare(last.getLatitude(), first.getLatitude()) == 0
                && Double.compare(last.getLongitude(), first.getLongitude()) == 0;

        int startIndex = samePoint ? 1 : 0;
        for (int i = startIndex; i < safeCandidate.size(); i++) {
            merged.add(safeCandidate.get(i));
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private NodeRef toNodeRef(RoadNode node) {
        return new NodeRef(node.getId(), node.getLatitude(), node.getLongitude());
    }

    private record RoadGraph(Long cityId, Route.TransportMode transportMode, Map<Long, List<EdgeState>> adjacency) {
    }

    private record NodeRef(Long id, Double latitude, Double longitude) {
    }

    private record EdgeState(NodeRef to, double distanceKm, int durationSec, List<LatLngDto> coordinates) {
    }

    private record NodeState(NodeRef node, double fScore, double gScore) {
    }

    private record StateRef(NodeRef previousNode, EdgeState edge) {
    }

    private record PathResult(List<LatLngDto> coordinates, double distanceKm, int durationSec) {
    }

    private record PathMeta(double distanceKm, int durationSec) {
    }
}
