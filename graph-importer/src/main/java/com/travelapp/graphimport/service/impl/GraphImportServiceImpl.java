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
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportServiceImpl implements GraphImportService {

    private final CityGraphVersionRepository graphVersionRepository;
    private final RoadNodeRepository roadNodeRepository;
    private final RoadEdgeRepository roadEdgeRepository;
    private final PoiGraphBindingRepository poiGraphBindingRepository;
    private final InternalPoiClient poiClient;
    private final RouteCacheClient routeCacheClient;
    private final ObjectMapper objectMapper;

    @Value("${graph-import.bbox-padding-deg:0.03}")
    private double bboxPaddingDeg;

    @Value("${graph-import.walk-speed-mps:1.4}")
    private double walkSpeedMps;

    @Value("${graph-import.mixed-speed-mps:1.2}")
    private double mixedSpeedMps;

    @Value("${graph-import.car-speed-mps:8.3}")
    private double carSpeedMps;

    @Value("${graph-import.max-archived-versions:2}")
    private int maxArchivedVersions;

    @Value("${graph-import.allowed-osm-root:/osm-data}")
    private String allowedOsmRoot;

    @Override
    @Transactional
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
            ParsedGraph parsedGraph = parseOsmXml(osmPath, request.getCityId(), version, bbox);
            roadNodeRepository.saveAll(parsedGraph.nodesById().values());
            roadEdgeRepository.saveAll(parsedGraph.edges());
            int boundPois = bindPois(request.getCityId(), version, pois);

            graphVersionRepository.archiveActiveByCityId(request.getCityId());
            version.setStatus(CityGraphVersion.Status.ACTIVE);
            version.setImportedAt(LocalDateTime.now());
            version.setFailureReason(null);
            graphVersionRepository.save(version);

            cleanupArchivedGraphs(request.getCityId());
            evictRouteCacheQuietly(request.getCityId());

            log.info("Graph import completed: cityId={}, versionId={}, nodes={}, edges={}, bindings={}, tookMs={}",
                    request.getCityId(),
                    version.getId(),
                    parsedGraph.nodesById().size(),
                    parsedGraph.edges().size(),
                    boundPois,
                    System.currentTimeMillis() - startedAt);
            return version.getId();
        } catch (Exception ex) {
            version.setStatus(CityGraphVersion.Status.FAILED);
            version.setFailureReason(limit(ex.getMessage(), 1000));
            graphVersionRepository.save(version);
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
        CityGraphVersion version = new CityGraphVersion();
        version.setCityId(cityId);
        version.setVersionNo(graphVersionRepository.findMaxVersionNo(cityId) + 1);
        version.setStatus(CityGraphVersion.Status.DRAFT);
        version.setBboxMinLat(bbox.minLat());
        version.setBboxMinLng(bbox.minLng());
        version.setBboxMaxLat(bbox.maxLat());
        version.setBboxMaxLng(bbox.maxLng());
        return graphVersionRepository.save(version);
    }

    private int bindPois(Long cityId, CityGraphVersion version, List<InternalPoiLiteResponse> pois) {
        int bound = 0;
        poiGraphBindingRepository.deleteByCityIdAndGraphVersionId(cityId, version.getId());
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
            poiGraphBindingRepository.save(binding);
            bound++;
        }
        return bound;
    }

    private ParsedGraph parseOsmXml(Path filePath, Long cityId, CityGraphVersion version, Bbox bbox) throws Exception {
        Map<Long, OsmNode> osmNodes = new HashMap<>();
        List<OsmWay> ways = new ArrayList<>();
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new FileInputStream(filePath.toFile()));
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
        return buildGraph(cityId, version, osmNodes, ways);
    }

    private ParsedGraph buildGraph(Long cityId, CityGraphVersion version, Map<Long, OsmNode> osmNodes, List<OsmWay> ways) throws Exception {
        Map<Long, RoadNode> nodesByOsmId = new HashMap<>();
        List<RoadEdge> edges = new ArrayList<>();
        for (OsmWay way : ways) {
            for (int i = 1; i < way.nodeRefs.size(); i++) {
                OsmNode a = osmNodes.get(way.nodeRefs.get(i - 1));
                OsmNode b = osmNodes.get(way.nodeRefs.get(i));
                if (a == null || b == null) {
                    continue;
                }
                RoadNode from = nodesByOsmId.computeIfAbsent(a.id, id -> toRoadNode(cityId, version, a));
                RoadNode to = nodesByOsmId.computeIfAbsent(b.id, id -> toRoadNode(cityId, version, b));

                RoadEdge edge = new RoadEdge();
                edge.setCityId(cityId);
                edge.setGraphVersion(version);
                edge.setFromNode(from);
                edge.setToNode(to);
                edge.setLengthM(haversineMeters(a.lat, a.lon, b.lat, b.lon));
                edge.setWalkAllowed(isWalkAllowed(way));
                edge.setMixedAllowed(isWalkAllowed(way));
                edge.setCarAllowed(isCarAllowed(way));
                edge.setPublicTransportAllowed(false);
                edge.setBidirectional(!"yes".equalsIgnoreCase(way.tags.getOrDefault("oneway", "no")));
                edge.setWalkTimeSec((int) Math.ceil(edge.getLengthM() / walkSpeedMps));
                edge.setMixedTimeSec((int) Math.ceil(edge.getLengthM() / mixedSpeedMps));
                edge.setCarTimeSec(edge.getCarAllowed() ? (int) Math.ceil(edge.getLengthM() / carSpeedMps) : null);
                edge.setPublicTransportTimeSec(null);
                edge.setGeom(toLineStringWkt(List.of(a, b)));
                edge.setPolylineJson(toPolylineJson(List.of(a, b)));
                edges.add(edge);
            }
        }
        return new ParsedGraph(nodesByOsmId, edges);
    }

    private RoadNode toRoadNode(Long cityId, CityGraphVersion version, OsmNode node) {
        RoadNode rn = new RoadNode();
        rn.setCityId(cityId);
        rn.setGraphVersion(version);
        rn.setLatitude(node.lat);
        rn.setLongitude(node.lon);
        rn.setGeom(toPointWkt(node.lat, node.lon));
        return rn;
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

    private String toLineStringWkt(List<OsmNode> nodes) {
        return "LINESTRING(" + nodes.stream().map(node -> node.lon + " " + node.lat).reduce((a, b) -> a + ", " + b).orElseThrow() + ")";
    }

    private String toPolylineJson(List<OsmNode> nodes) throws Exception {
        List<Map<String, Double>> coords = nodes.stream()
                .map(node -> Map.of(
                        "latitude", node.lat,
                        "longitude", node.lon
                ))
                .toList();
        return objectMapper.writeValueAsString(coords);
    }

    private boolean isSupportedWay(OsmWay way) {
        String highway = way.tags.get("highway");
        return highway != null && Set.of("residential", "living_street", "service", "pedestrian", "footway", "path", "cycleway", "unclassified", "tertiary", "secondary", "primary").contains(highway);
    }

    private boolean isWalkAllowed(OsmWay way) {
        String highway = way.tags.get("highway");
        return !"motorway".equals(highway);
    }

    private boolean isCarAllowed(OsmWay way) {
        String highway = way.tags.get("highway");
        return !Set.of("footway", "path", "pedestrian", "steps", "cycleway").contains(highway);
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record OsmNode(long id, double lat, double lon) {}

    private static class OsmWay {
        long id;
        List<Long> nodeRefs = new ArrayList<>();
        Map<String, String> tags = new HashMap<>();

        OsmWay(long id) {
            this.id = id;
        }
    }

    private record ParsedGraph(Map<Long, RoadNode> nodesById, List<RoadEdge> edges) {}

    private record Bbox(double minLat, double minLng, double maxLat, double maxLng) {
        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }

        static Bbox fromPois(List<InternalPoiLiteResponse> pois, double pad) {
            double minLat = pois.stream().mapToDouble(InternalPoiLiteResponse::getLatitude).min().orElseThrow() - pad;
            double minLng = pois.stream().mapToDouble(InternalPoiLiteResponse::getLongitude).min().orElseThrow() - pad;
            double maxLat = pois.stream().mapToDouble(InternalPoiLiteResponse::getLatitude).max().orElseThrow() + pad;
            double maxLng = pois.stream().mapToDouble(InternalPoiLiteResponse::getLongitude).max().orElseThrow() + pad;
            return new Bbox(minLat, minLng, maxLat, maxLng);
        }
    }
}
