package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.entity.CityGraphVersion;
import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import com.travelapp.graphimport.model.entity.RoadEdge;
import com.travelapp.graphimport.model.entity.RoadNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphImportPersistenceServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private GraphImportPersistenceService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "batchSize", 2);
    }

    @Test
    void persistNodes_shouldPersistEveryNodeAndFlushByBatch() {
        List<RoadNode> nodes = List.of(node(1L), node(2L), node(3L));

        service.persistNodes(nodes);

        nodes.forEach(node -> verify(entityManager).persist(node));
        verify(entityManager, times(2)).flush();
    }

    @Test
    void persistEdges_shouldSkipEmptyList() {
        service.persistEdges(List.of());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void persistEdges_shouldSkipInvalidEdgesWithoutNodeIds() {
        RoadEdge invalid = new RoadEdge();
        invalid.setFromNode(node(null));
        invalid.setToNode(node(2L));

        service.persistEdges(List.of(invalid));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void persistEdges_shouldBatchOnlyValidEdges() {
        RoadNode from = node(1L);
        RoadNode to = node(2L);
        RoadEdge valid = edge(from, to);
        RoadEdge selfLoop = edge(from, from);

        service.persistEdges(List.of(valid, selfLoop));

        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void persistBindings_shouldSkipEmptyList() {
        service.persistBindings(List.of());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void persistBindings_shouldUseBatchUpdate_whenBindingsExist() {
        service.persistBindings(List.of(binding()));

        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    private RoadNode node(Long id) {
        RoadNode node = new RoadNode();
        node.setId(id);
        node.setCityId(10L);
        node.setLatitude(54.3);
        node.setLongitude(48.4);
        node.setGeomWkt("POINT(48.4 54.3)");
        return node;
    }

    private RoadEdge edge(RoadNode from, RoadNode to) {
        CityGraphVersion version = new CityGraphVersion();
        version.setId(55L);
        RoadEdge edge = new RoadEdge();
        edge.setCityId(10L);
        edge.setGraphVersion(version);
        edge.setFromNode(from);
        edge.setToNode(to);
        edge.setLengthM(100.0);
        edge.setWalkAllowed(true);
        edge.setCarAllowed(false);
        edge.setMixedAllowed(true);
        edge.setPublicTransportAllowed(false);
        edge.setWalkTimeSec(72);
        edge.setBidirectional(true);
        edge.setGeomWkt("LINESTRING(48.4 54.3, 48.5 54.4)");
        edge.setPolylineJson("[]");
        edge.setSource("OSM");
        return edge;
    }

    private PoiGraphBinding binding() {
        CityGraphVersion version = new CityGraphVersion();
        version.setId(55L);
        PoiGraphBinding binding = new PoiGraphBinding();
        binding.setPoiId(100L);
        binding.setCityId(10L);
        binding.setGraphVersion(version);
        binding.setNearestNode(node(1L));
        binding.setSnappedLatitude(54.3);
        binding.setSnappedLongitude(48.4);
        binding.setSnapDistanceM(15.0);
        return binding;
    }
}
