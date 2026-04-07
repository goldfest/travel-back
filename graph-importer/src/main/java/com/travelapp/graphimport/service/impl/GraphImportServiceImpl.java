package com.travelapp.graphimport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.graphimport.client.InternalPoiClient;
import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.model.dto.InternalPoiLiteResponse;
import com.travelapp.graphimport.model.entity.CityGraphVersion;
import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import com.travelapp.graphimport.model.entity.RoadEdge;
import com.travelapp.graphimport.model.entity.RoadNode;
import com.travelapp.graphimport.repository.CityGraphVersionRepository;
import com.travelapp.graphimport.repository.PoiGraphBindingRepository;
import com.travelapp.graphimport.repository.RoadEdgeRepository;
import com.travelapp.graphimport.repository.RoadNodeRepository;
import com.travelapp.graphimport.service.GraphImportService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
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
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${graph-import.bbox-padding-deg:0.03}")
    private double bboxPaddingDeg;

    @Value("${graph-import.walk-speed-mps:1.4}")
    private double walkSpeedMps;

    @Value("${graph-import.mixed-speed-mps:1.2}")
    private double mixedSpeedMps;

    @Value("${graph-import.car-speed-mps:8.3}")
    private double carSpeedMps;

    @Override
    @Transactional
    public Long importCityGraph(GraphImportRequest request) {
        List<InternalPoiLiteResponse> pois = poiClient.getCityPois(request.getCityId(), true, true);
        if (pois.isEmpty()) {
            throw new IllegalStateException("Для города нет активных POI, невозможно вычислить bbox");
        }

        Bbox bbox = Bbox.fromPois(pois, bboxPaddingDeg);
        CityGraphVersion version = new CityGraphVersion();
        version.setCityId(request.getCityId());
        version.setVersionNo(graphVersionRepository.findMaxVersionNo(request.getCityId()) + 1);
        version.setStatus(CityGraphVersion.Status.DRAFT);
        version.setBboxMinLat(bbox.minLat());
        version.setBboxMinLng(bbox.minLng());
        version.setBboxMaxLat(bbox.maxLat());
        version.setBboxMaxLng(bbox.maxLng());
        version = graphVersionRepository.save(version);

        ParsedGraph parsedGraph = parseOsmXml(request.getOsmFilePath(), request.getCityId(), version, bbox);
        roadNodeRepository.saveAll(parsedGraph.nodesById().values());
        roadEdgeRepository.saveAll(parsedGraph.edges());
        bindPois(request.getCityId(), version, pois);

        graphVersionRepository.archiveActiveByCityId(request.getCityId());
        version.setStatus(CityGraphVersion.Status.ACTIVE);
        version.setImportedAt(LocalDateTime.now());
        graphVersionRepository.save(version);
        return version.getId();
    }

    private void bindPois(Long cityId, CityGraphVersion version, List<InternalPoiLiteResponse> pois) {
        poiGraphBindingRepository.deleteByCityIdAndGraphVersionId(cityId, version.getId());
        for (InternalPoiLiteResponse poi : pois) {
            Optional<RoadNode> nearest = roadNodeRepository.findNearestNode(cityId, version.getId(), poi.getLatitude(), poi.getLongitude());
            if (nearest.isEmpty()) continue;
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
        }
    }

    private ParsedGraph parseOsmXml(String filePath, Long cityId, CityGraphVersion version, Bbox bbox) {
        try {
            Map<Long, OsmNode> osmNodes = new HashMap<>();
            List<OsmWay> ways = new ArrayList<>();
            XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(new FileInputStream(filePath));
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
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка импорта OSM XML: " + ex.getMessage(), ex);
        }
    }

    private ParsedGraph buildGraph(Long cityId, CityGraphVersion version, Map<Long, OsmNode> osmNodes, List<OsmWay> ways) throws Exception {
        Map<Long, RoadNode> nodesByOsmId = new HashMap<>();
        List<RoadEdge> edges = new ArrayList<>();
        for (OsmWay way : ways) {
            for (int i = 1; i < way.nodeRefs.size(); i++) {
                OsmNode a = osmNodes.get(way.nodeRefs.get(i - 1));
                OsmNode b = osmNodes.get(way.nodeRefs.get(i));
                if (a == null || b == null) continue;
                RoadNode from = nodesByOsmId.computeIfAbsent(a.id, id -> toRoadNode(cityId, version, a));
                RoadNode to = nodesByOsmId.computeIfAbsent(b.id, id -> toRoadNode(cityId, version, b));
                RoadEdge edge = new RoadEdge();
                edge.setCityId(cityId);
                edge.setGraphVersion(version);
                edge.setFromNode(from);
                edge.setToNode(to);
                edge.setLengthM(haversineMeters(a.lat, a.lon, b.lat, b.lon));
                edge.setWalkAllowed(isWalkAllowed(way));
                edge.setMixedAllowed(true);
                edge.setCarAllowed(isCarAllowed(way));
                edge.setPublicTransportAllowed(false);
                edge.setBidirectional(!"yes".equalsIgnoreCase(way.tags.getOrDefault("oneway", "no")));
                edge.setWalkTimeSec((int) Math.ceil(edge.getLengthM() / walkSpeedMps));
                edge.setMixedTimeSec((int) Math.ceil(edge.getLengthM() / mixedSpeedMps));
                edge.setCarTimeSec(edge.getCarAllowed() ? (int) Math.ceil(edge.getLengthM() / carSpeedMps) : null);
                edge.setPolylineJson(objectMapper.writeValueAsString(List.of(
                        Map.of("lat", a.lat, "lng", a.lon),
                        Map.of("lat", b.lat, "lng", b.lon)
                )));
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
        entityManager.persist(rn);
        entityManager.flush();
        entityManager.createNativeQuery("UPDATE road_nodes SET geom = ST_SetSRID(ST_MakePoint(?1, ?2), 4326) WHERE id = ?3")
                .setParameter(1, node.lon)
                .setParameter(2, node.lat)
                .setParameter(3, rn.getId())
                .executeUpdate();
        return rn;
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
        final double R = 6371000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record OsmNode(long id, double lat, double lon) {}
    private static class OsmWay {
        long id;
        List<Long> nodeRefs = new ArrayList<>();
        Map<String, String> tags = new HashMap<>();
        OsmWay(long id) { this.id = id; }
    }
    private record ParsedGraph(Map<Long, RoadNode> nodesById, List<RoadEdge> edges) {}
    private record Bbox(double minLat, double minLng, double maxLat, double maxLng) {
        boolean contains(double lat, double lng) { return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng; }
        static Bbox fromPois(List<InternalPoiLiteResponse> pois, double pad) {
            double minLat = pois.stream().mapToDouble(InternalPoiLiteResponse::getLatitude).min().orElseThrow() - pad;
            double minLng = pois.stream().mapToDouble(InternalPoiLiteResponse::getLongitude).min().orElseThrow() - pad;
            double maxLat = pois.stream().mapToDouble(InternalPoiLiteResponse::getLatitude).max().orElseThrow() + pad;
            double maxLng = pois.stream().mapToDouble(InternalPoiLiteResponse::getLongitude).max().orElseThrow() + pad;
            return new Bbox(minLat, minLng, maxLat, maxLng);
        }
    }
}
