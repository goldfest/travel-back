package com.travelapp.graphimport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.graphimport.client.InternalPoiClient;
import com.travelapp.graphimport.client.RouteCacheClient;
import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.model.dto.InternalPoiLiteResponse;
import com.travelapp.graphimport.model.dto.response.GraphImportStatsResponse;
import com.travelapp.graphimport.model.entity.CityGraphVersion;
import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import com.travelapp.graphimport.model.entity.RoadEdge;
import com.travelapp.graphimport.model.entity.RoadNode;
import com.travelapp.graphimport.repository.CityGraphVersionRepository;
import com.travelapp.graphimport.repository.PoiGraphBindingRepository;
import com.travelapp.graphimport.repository.RoadEdgeRepository;
import com.travelapp.graphimport.repository.RoadNodeRepository;
import com.travelapp.graphimport.service.GraphImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportServiceImpl implements GraphImportService {

    private static final Set<String> WALK_HIGHWAYS = Set.of(
            "footway", "path", "pedestrian", "living_street", "residential", "service",
            "track", "unclassified", "tertiary", "tertiary_link", "secondary", "secondary_link",
            "primary", "primary_link", "road", "steps", "cycleway"
    );

    private static final Set<String> CAR_HIGHWAYS = Set.of(
            "motorway", "motorway_link", "trunk", "trunk_link",
            "primary", "primary_link", "secondary", "secondary_link",
            "tertiary", "tertiary_link", "unclassified",
            "residential", "living_street", "service", "road"
    );

    private static final Set<String> SUPPORTED_HIGHWAYS;

    static {
        Set<String> supported = new HashSet<>(WALK_HIGHWAYS);
        supported.addAll(CAR_HIGHWAYS);
        SUPPORTED_HIGHWAYS = Collections.unmodifiableSet(supported);
    }

    private final CityGraphVersionRepository graphVersionRepository;
    private final RoadNodeRepository roadNodeRepository;
    private final RoadEdgeRepository roadEdgeRepository;
    private final PoiGraphBindingRepository poiGraphBindingRepository;
    private final InternalPoiClient poiClient;
    private final RouteCacheClient routeCacheClient;
    private final ObjectMapper objectMapper;
    private final GraphImportPersistenceService persistenceService;
    private final GraphImportProgressService progressService;

    @Value("${graph-import.bbox-padding-deg:0.03}")
    private double bboxPaddingDeg;

    @Value("${graph-import.walk-speed-mps:1.4}")
    private double walkSpeedMps;

    @Value("${graph-import.mixed-speed-mps:1.2}")
    private double mixedSpeedMps;

    @Value("${graph-import.car-speed-mps:8.3}")
    private double defaultCarSpeedMps;

    @Value("${graph-import.max-archived-versions:2}")
    private int maxArchivedVersions;

    @Value("${graph-import.allowed-osm-root:/osm-data}")
    private String allowedOsmRoot;

    @Override
    public Long importCityGraph(GraphImportRequest request) {
        Path osmPath = resolveAndValidateOsmPath(request.getCityId(), request.getOsmFilePath());

        List<InternalPoiLiteResponse> pois = poiClient.getCityPois(request.getCityId(), true, true);
        if (pois.isEmpty()) {
            throw new IllegalStateException("Для города нет активных POI, невозможно вычислить bbox");
        }

        Bbox bbox = Bbox.fromPois(pois, bboxPaddingDeg);
        CityGraphVersion version = createDraftVersion(request.getCityId(), bbox);

        try {
            long startedAt = System.currentTimeMillis();
            log.info("Graph import started: cityId={}, versionId={}, osmPath={}, bbox=[{}, {}]-[{}, {}], poiCount={}",
                    request.getCityId(),
                    version.getId(),
                    osmPath,
                    bbox.minLat(),
                    bbox.minLng(),
                    bbox.maxLat(),
                    bbox.maxLng(),
                    pois.size());

            long parseStartedAt = System.currentTimeMillis();
            log.info("Graph import phase started: cityId={}, versionId={}, phase=PARSING_OSM", request.getCityId(), version.getId());
            ParsedGraph parsedGraph = parseOsmXml(osmPath, request.getCityId(), version, bbox);
            long parseMs = System.currentTimeMillis() - parseStartedAt;

            long saveNodesStartedAt = System.currentTimeMillis();
            log.info("Graph import phase started: cityId={}, versionId={}, phase=PERSIST_NODES, nodes={}", request.getCityId(), version.getId(), parsedGraph.nodesByOsmId().size());
            persistenceService.persistNodes(parsedGraph.nodesByOsmId().values());
            long saveNodesMs = System.currentTimeMillis() - saveNodesStartedAt;

            long saveEdgesStartedAt = System.currentTimeMillis();
            log.info("Graph import phase started: cityId={}, versionId={}, phase=PERSIST_EDGES, edges={}", request.getCityId(), version.getId(), parsedGraph.edges().size());
            persistenceService.persistEdges(parsedGraph.edges());
            long saveEdgesMs = System.currentTimeMillis() - saveEdgesStartedAt;

            long bindStartedAt = System.currentTimeMillis();
            log.info("Graph import phase started: cityId={}, versionId={}, phase=BIND_POIS, poiCount={}", request.getCityId(), version.getId(), pois.size());
            int boundPois = bindPois(request.getCityId(), version, pois);
            long bindMs = System.currentTimeMillis() - bindStartedAt;

            log.info("Graph import phase started: cityId={}, versionId={}, phase=ACTIVATE_VERSION", request.getCityId(), version.getId());
            progressService.activateVersion(version.getId());
            cleanupArchivedGraphs(request.getCityId());
            evictRouteCacheQuietly(request.getCityId());

            long totalMs = System.currentTimeMillis() - startedAt;
            log.info(
                    "Graph import completed: cityId={}, versionId={}, nodes={}, edges={}, bindings={}, parseMs={}, saveNodesMs={}, saveEdgesMs={}, bindPoisMs={}, totalMs={}",
                    request.getCityId(),
                    version.getId(),
                    parsedGraph.nodesByOsmId().size(),
                    parsedGraph.edges().size(),
                    boundPois,
                    parseMs,
                    saveNodesMs,
                    saveEdgesMs,
                    bindMs,
                    totalMs
            );
            return version.getId();
        } catch (Exception ex) {
            String failureReason = limit(ex.getMessage(), 1000);
            String failureMessage = "Ошибка загрузки графа дорог: " + limit(ex.getMessage(), 240);
            try {
                persistenceService.deleteGraphData(request.getCityId(), version.getId());
            } catch (Exception cleanupEx) {
                log.warn("Could not clean failed graph import data: cityId={}, versionId={}, error={}",
                        request.getCityId(), version.getId(), cleanupEx.getMessage());
            }
            progressService.markFailed(version.getId(), failureReason, failureMessage);
            log.error("Graph import failed: cityId={}, versionId={}", request.getCityId(), version.getId(), ex);
            throw ex instanceof RuntimeException re ? re : new IllegalStateException("Ошибка импорта графа", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GraphImportStatsResponse getCityImportStats(Long cityId) {
        Optional<CityGraphVersion> activeVersion = graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE);
        if (activeVersion.isEmpty()) {
            Optional<CityGraphVersion> failedVersion = graphVersionRepository.findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.FAILED);
            CityGraphVersion version = failedVersion.orElse(null);
            return GraphImportStatsResponse.builder()
                    .cityId(cityId)
                    .activeGraphVersionId(version == null ? null : version.getId())
                    .activeGraphVersionNo(version == null ? null : version.getVersionNo())
                    .activeGraphStatus(version == null ? "MISSING" : version.getStatus().name())
                    .importedAt(version == null ? null : version.getImportedAt())
                    .nodeCount(0L)
                    .edgeCount(0L)
                    .bindingCount(0L)
                    .failureReason(version == null ? null : version.getFailureReason())
                    .build();
        }

        CityGraphVersion version = activeVersion.get();
        return GraphImportStatsResponse.builder()
                .cityId(cityId)
                .activeGraphVersionId(version.getId())
                .activeGraphVersionNo(version.getVersionNo())
                .activeGraphStatus(version.getStatus().name())
                .importedAt(version.getImportedAt())
                .nodeCount(roadNodeRepository.countByGraphVersion_Id(version.getId()))
                .edgeCount(roadEdgeRepository.countByGraphVersion_Id(version.getId()))
                .bindingCount(poiGraphBindingRepository.countByGraphVersion_Id(version.getId()))
                .failureReason(version.getFailureReason())
                .build();
    }

    private CityGraphVersion createDraftVersion(Long cityId, Bbox bbox) {
        return progressService.createDraftVersion(
                cityId,
                graphVersionRepository.findMaxVersionNo(cityId) + 1,
                bbox.minLat(),
                bbox.minLng(),
                bbox.maxLat(),
                bbox.maxLng()
        );
    }


    private int bindPois(Long cityId, CityGraphVersion version, List<InternalPoiLiteResponse> pois) {
        persistenceService.deleteBindings(cityId, version.getId());

        List<PoiGraphBinding> bindings = new ArrayList<>();
        for (InternalPoiLiteResponse poi : pois) {
            Optional<RoadNode> nearest = roadNodeRepository.findNearestNode(cityId, version.getId(), poi.getLatitude(), poi.getLongitude());
            if (nearest.isEmpty()) {
                continue;
            }

            RoadNode node = nearest.get();
            PoiGraphBinding binding = new PoiGraphBinding();
            binding.setPoiId(poi.getId());
            binding.setCityId(cityId);
            binding.setGraphVersion(version);
            binding.setNearestNode(node);
            binding.setSnappedLatitude(node.getLatitude());
            binding.setSnappedLongitude(node.getLongitude());
            binding.setSnapDistanceM(haversineMeters(poi.getLatitude(), poi.getLongitude(), node.getLatitude(), node.getLongitude()));
            bindings.add(binding);
        }

        persistenceService.persistBindings(bindings);
        return bindings.size();
    }

    private ParsedGraph parseOsmXml(Path filePath, Long cityId, CityGraphVersion version, Bbox bbox) throws Exception {
        Map<Long, OsmNode> osmNodes = new HashMap<>();
        List<OsmWay> ways = new ArrayList<>();

        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            OsmWay currentWay = null;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("node".equals(name)) {
                        long id = Long.parseLong(reader.getAttributeValue(null, "id"));
                        double lat = Double.parseDouble(reader.getAttributeValue(null, "lat"));
                        double lon = Double.parseDouble(reader.getAttributeValue(null, "lon"));
                        if (bbox.contains(lat, lon)) {
                            osmNodes.put(id, new OsmNode(id, lat, lon));
                        }
                    } else if ("way".equals(name)) {
                        currentWay = new OsmWay(Long.parseLong(reader.getAttributeValue(null, "id")));
                    } else if (currentWay != null && "nd".equals(name)) {
                        currentWay.nodeRefs.add(Long.parseLong(reader.getAttributeValue(null, "ref")));
                    } else if (currentWay != null && "tag".equals(name)) {
                        currentWay.tags.put(reader.getAttributeValue(null, "k"), reader.getAttributeValue(null, "v"));
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "way".equals(reader.getLocalName()) && currentWay != null) {
                    if (isSupportedWay(currentWay)) {
                        ways.add(currentWay);
                    }
                    currentWay = null;
                }
            }
            reader.close();
        }

        return buildGraph(cityId, version, osmNodes, ways);
    }

    private ParsedGraph buildGraph(Long cityId, CityGraphVersion version, Map<Long, OsmNode> osmNodes, List<OsmWay> ways) throws Exception {
        Map<Long, Integer> nodeUsageCount = new HashMap<>();
        for (OsmWay way : ways) {
            for (Long nodeRef : way.nodeRefs) {
                if (osmNodes.containsKey(nodeRef)) {
                    nodeUsageCount.merge(nodeRef, 1, Integer::sum);
                }
            }
        }

        Map<Long, RoadNode> nodesByOsmId = new LinkedHashMap<>();
        List<RoadEdge> edges = new ArrayList<>();

        for (OsmWay way : ways) {
            WayProfile profile = buildProfile(way);
            if (!profile.walkAllowed() && !profile.carAllowed()) {
                continue;
            }

            List<OsmNode> pathNodes = materializeWayNodes(way, osmNodes, profile.reverse());
            if (pathNodes.size() < 2) {
                continue;
            }

            int anchorStartIndex = 0;
            for (int i = 1; i < pathNodes.size(); i++) {
                if (!isAnchor(pathNodes, nodeUsageCount, i)) {
                    continue;
                }

                List<OsmNode> segmentNodes = pathNodes.subList(anchorStartIndex, i + 1);
                if (segmentNodes.size() >= 2) {
                    createCompressedEdge(cityId, version, nodesByOsmId, edges, segmentNodes, profile);
                }
                anchorStartIndex = i;
            }
        }

        return new ParsedGraph(nodesByOsmId, edges);
    }

    private void createCompressedEdge(Long cityId,
                                      CityGraphVersion version,
                                      Map<Long, RoadNode> nodesByOsmId,
                                      List<RoadEdge> edges,
                                      List<OsmNode> segmentNodes,
                                      WayProfile profile) throws Exception {
        if (segmentNodes == null || segmentNodes.size() < 2) {
            return;
        }

        OsmNode start = segmentNodes.get(0);
        OsmNode end = segmentNodes.get(segmentNodes.size() - 1);

        long uniqueNodeCount = segmentNodes.stream()
                .map(OsmNode::id)
                .distinct()
                .count();

        if (uniqueNodeCount < 2) {
            return;
        }

        if (start.id() == end.id()) {
            log.debug("Skipping self-loop compressed edge: osmNodeId={}, points={}", start.id(), segmentNodes.size());
            return;
        }

        RoadNode from = nodesByOsmId.computeIfAbsent(start.id(), ignored -> toRoadNode(cityId, version, start));
        RoadNode to = nodesByOsmId.computeIfAbsent(end.id(), ignored -> toRoadNode(cityId, version, end));

        double lengthM = polylineLengthMeters(segmentNodes);
        if (lengthM <= 0.0d) {
            return;
        }

        RoadEdge edge = new RoadEdge();
        edge.setCityId(cityId);
        edge.setGraphVersion(version);
        edge.setFromNode(from);
        edge.setToNode(to);
        edge.setLengthM(lengthM);
        edge.setWalkAllowed(profile.walkAllowed());
        edge.setMixedAllowed(profile.walkAllowed());
        edge.setCarAllowed(profile.carAllowed());
        edge.setPublicTransportAllowed(false);
        edge.setBidirectional(profile.bidirectional());
        edge.setWalkTimeSec(profile.walkAllowed() ? (int) Math.ceil(lengthM / walkSpeedMps) : null);
        edge.setMixedTimeSec(profile.walkAllowed() ? (int) Math.ceil(lengthM / mixedSpeedMps) : null);
        edge.setCarTimeSec(profile.carAllowed() ? (int) Math.ceil(lengthM / profile.carSpeedMps()) : null);
        edge.setPublicTransportTimeSec(null);
        edge.setGeomWkt(toLineStringWkt(segmentNodes));
        edge.setPolylineJson(toPolylineJson(segmentNodes));
        edge.setSource("OSM");

        edges.add(edge);
    }

    private RoadNode toRoadNode(Long cityId, CityGraphVersion version, OsmNode node) {
        RoadNode roadNode = new RoadNode();
        roadNode.setCityId(cityId);
        roadNode.setGraphVersion(version);
        roadNode.setLatitude(node.lat());
        roadNode.setLongitude(node.lon());
        roadNode.setGeomWkt(toPointWkt(node.lat(), node.lon()));
        return roadNode;
    }

    private boolean isAnchor(List<OsmNode> pathNodes, Map<Long, Integer> nodeUsageCount, int index) {
        return index == pathNodes.size() - 1
                || index == 0
                || nodeUsageCount.getOrDefault(pathNodes.get(index).id(), 0) > 1;
    }

    private List<OsmNode> materializeWayNodes(OsmWay way, Map<Long, OsmNode> osmNodes, boolean reverse) {
        List<Long> refs = reverse ? reverseRefs(way.nodeRefs) : way.nodeRefs;
        List<OsmNode> result = new ArrayList<>(refs.size());
        for (Long nodeRef : refs) {
            OsmNode node = osmNodes.get(nodeRef);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    private List<Long> reverseRefs(List<Long> refs) {
        List<Long> reversed = new ArrayList<>(refs);
        Collections.reverse(reversed);
        return reversed;
    }

    private WayProfile buildProfile(OsmWay way) {
        boolean walkAllowed = isWalkAllowed(way);
        boolean carAllowed = isCarAllowed(way);
        String oneway = normalizeTag(way.tags.get("oneway"));
        boolean reverse = "-1".equals(oneway);
        boolean bidirectional = !("yes".equals(oneway) || "1".equals(oneway) || "true".equals(oneway) || reverse);
        double carSpeed = resolveCarSpeedMps(way);
        return new WayProfile(walkAllowed, carAllowed, bidirectional, reverse, carSpeed);
    }

    private boolean isSupportedWay(OsmWay way) {
        String highway = normalizeTag(way.tags.get("highway"));
        return highway != null && SUPPORTED_HIGHWAYS.contains(highway);
    }

    private boolean isWalkAllowed(OsmWay way) {
        String highway = normalizeTag(way.tags.get("highway"));
        if (highway == null || !WALK_HIGHWAYS.contains(highway)) {
            return false;
        }
        String foot = normalizeTag(way.tags.get("foot"));
        String access = normalizeTag(way.tags.get("access"));
        return !"no".equals(foot) && !"private".equals(access);
    }

    private boolean isCarAllowed(OsmWay way) {
        String highway = normalizeTag(way.tags.get("highway"));
        if (highway == null || !CAR_HIGHWAYS.contains(highway)) {
            return false;
        }

        String motorVehicle = normalizeTag(way.tags.get("motor_vehicle"));
        String vehicle = normalizeTag(way.tags.get("vehicle"));
        String access = normalizeTag(way.tags.get("access"));
        if ("no".equals(motorVehicle) || "no".equals(vehicle) || "no".equals(access)) {
            return false;
        }
        if ("private".equals(access)) {
            return false;
        }
        return true;
    }

    private double resolveCarSpeedMps(OsmWay way) {
        Double explicit = parseMaxSpeedMps(way.tags.get("maxspeed"));
        if (explicit != null && explicit > 0.0d) {
            return explicit;
        }

        String highway = normalizeTag(way.tags.get("highway"));
        if (highway == null) {
            return defaultCarSpeedMps;
        }

        return switch (highway) {
            case "motorway", "motorway_link" -> 27.8d;
            case "trunk", "trunk_link" -> 22.2d;
            case "primary", "primary_link" -> 19.4d;
            case "secondary", "secondary_link" -> 16.7d;
            case "tertiary", "tertiary_link" -> 13.9d;
            case "residential", "living_street" -> 8.3d;
            case "service" -> 5.5d;
            default -> defaultCarSpeedMps;
        };
    }

    private Double parseMaxSpeedMps(String rawValue) {
        String normalized = normalizeTag(rawValue);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        try {
            if (normalized.endsWith("mph")) {
                double mph = Double.parseDouble(normalized.replace("mph", "").trim());
                return mph * 0.44704d;
            }
            String numeric = normalized.replace("km/h", "").replace("kph", "").trim();
            if (numeric.matches("\\d+(\\.\\d+)?")) {
                return Double.parseDouble(numeric) / 3.6d;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String normalizeTag(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private double polylineLengthMeters(List<OsmNode> nodes) {
        double total = 0.0d;
        for (int i = 1; i < nodes.size(); i++) {
            OsmNode a = nodes.get(i - 1);
            OsmNode b = nodes.get(i);
            total += haversineMeters(a.lat(), a.lon(), b.lat(), b.lon());
        }
        return total;
    }

    private void cleanupArchivedGraphs(Long cityId) {
        List<CityGraphVersion> archived = graphVersionRepository.findArchivedByCityIdOrderByVersionNoDesc(cityId);
        if (archived.size() <= maxArchivedVersions) {
            return;
        }
        archived.stream()
                .skip(maxArchivedVersions)
                .forEach(version -> {
                    log.info("Removing archived graph version: cityId={}, versionId={}, versionNo={}", cityId, version.getId(), version.getVersionNo());
                    graphVersionRepository.delete(version);
                });
    }

    private void evictRouteCacheQuietly(Long cityId) {
        try {
            routeCacheClient.evictCityGraphCache(cityId);
        } catch (Exception ex) {
            log.warn("Route cache eviction request failed for cityId={}: {}", cityId, ex.getMessage());
        }
    }

    private Path resolveAndValidateOsmPath(Long cityId, String rawPath) {
        try {
            Path root = Path.of(allowedOsmRoot).toAbsolutePath().normalize();

            Path candidate;
            if (rawPath == null || rawPath.isBlank()) {
                candidate = resolveDefaultOsmPath(root, cityId);
            } else {
                candidate = Path.of(rawPath);
                if (!candidate.isAbsolute()) {
                    candidate = root.resolve(candidate);
                }
                candidate = candidate.toAbsolutePath().normalize();
            }

            if (!candidate.startsWith(root)) {
                throw new IllegalArgumentException("OSM-файл должен находиться внутри каталога " + root);
            }
            if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("OSM-файл не найден: " + candidate);
            }
            return candidate;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Некорректный путь к OSM-файлу: " + rawPath, ex);
        }
    }

    private Path resolveDefaultOsmPath(Path root, Long cityId) throws Exception {
        List<String> candidates = List.of(
                cityId + ".osm",
                cityId + ".osm.xml",
                "city-" + cityId + ".osm",
                "city_" + cityId + ".osm",
                "city" + cityId + ".osm"
        );

        for (String fileName : candidates) {
            Path candidate = root.resolve(fileName).toAbsolutePath().normalize();
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        try (var stream = Files.list(root)) {
            List<Path> osmFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".osm") || name.endsWith(".osm.xml");
                    })
                    .sorted()
                    .toList();

            if (osmFiles.size() == 1) {
                return osmFiles.get(0).toAbsolutePath().normalize();
            }
        }

        throw new IllegalArgumentException("Не удалось автоматически определить OSM-файл для города " + cityId
                + ". Укажи osmFilePath явно или положи один .osm-файл в " + root);
    }

    private String toPointWkt(double lat, double lon) {
        return "POINT(" + lon + " " + lat + ")";
    }

    private String toLineStringWkt(Collection<OsmNode> nodes) {
        return "LINESTRING(" + nodes.stream()
                .map(node -> node.lon() + " " + node.lat())
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow() + ")";
    }

    private String toPolylineJson(Collection<OsmNode> nodes) throws Exception {
        List<Map<String, Double>> coords = nodes.stream()
                .map(node -> Map.of("latitude", node.lat(), "longitude", node.lon()))
                .toList();
        return objectMapper.writeValueAsString(coords);
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusM = 6_371_000.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusM * c;
    }

    private String limit(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private record ParsedGraph(Map<Long, RoadNode> nodesByOsmId, List<RoadEdge> edges) {
    }

    private record WayProfile(boolean walkAllowed,
                              boolean carAllowed,
                              boolean bidirectional,
                              boolean reverse,
                              double carSpeedMps) {
    }

    private record Bbox(double minLat, double minLng, double maxLat, double maxLng) {
        static Bbox fromPois(List<InternalPoiLiteResponse> pois, double paddingDeg) {
            double minLat = Double.POSITIVE_INFINITY;
            double minLng = Double.POSITIVE_INFINITY;
            double maxLat = Double.NEGATIVE_INFINITY;
            double maxLng = Double.NEGATIVE_INFINITY;
            for (InternalPoiLiteResponse poi : pois) {
                minLat = Math.min(minLat, poi.getLatitude());
                minLng = Math.min(minLng, poi.getLongitude());
                maxLat = Math.max(maxLat, poi.getLatitude());
                maxLng = Math.max(maxLng, poi.getLongitude());
            }
            return new Bbox(minLat - paddingDeg, minLng - paddingDeg, maxLat + paddingDeg, maxLng + paddingDeg);
        }

        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }
    }

    private record OsmNode(long id, double lat, double lon) {
    }

    private static final class OsmWay {
        private final long id;
        private final List<Long> nodeRefs = new ArrayList<>();
        private final Map<String, String> tags = new HashMap<>();

        private OsmWay(long id) {
            this.id = id;
        }
    }
}
