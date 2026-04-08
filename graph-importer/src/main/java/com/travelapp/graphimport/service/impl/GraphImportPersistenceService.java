package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import com.travelapp.graphimport.model.entity.RoadEdge;
import com.travelapp.graphimport.model.entity.RoadNode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GraphImportPersistenceService {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Value("${graph-import.batch-size:1000}")
    private int batchSize;

    public void persistNodes(Collection<RoadNode> nodes) {
        int i = 0;
        for (RoadNode node : nodes) {
            entityManager.persist(node);
            i++;
            if (i % batchSize == 0) {
                entityManager.flush();
            }
        }
        entityManager.flush();
    }

    public void persistEdges(List<RoadEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        List<RoadEdge> filteredEdges = edges.stream()
                .filter(edge -> edge.getFromNode() != null)
                .filter(edge -> edge.getToNode() != null)
                .filter(edge -> edge.getFromNode().getId() != null)
                .filter(edge -> edge.getToNode().getId() != null)
                .filter(edge -> !edge.getFromNode().getId().equals(edge.getToNode().getId()))
                .toList();

        if (filteredEdges.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO road_edges (
                    city_id,
                    graph_version_id,
                    from_node_id,
                    to_node_id,
                    length_m,
                    walk_allowed,
                    car_allowed,
                    mixed_allowed,
                    public_transport_allowed,
                    walk_time_sec,
                    car_time_sec,
                    mixed_time_sec,
                    public_transport_time_sec,
                    bidirectional,
                    geom,
                    polyline_json,
                    source
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?::jsonb, ?)
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        RoadEdge edge = filteredEdges.get(i);
                        ps.setLong(1, edge.getCityId());
                        ps.setLong(2, edge.getGraphVersion().getId());
                        ps.setLong(3, edge.getFromNode().getId());
                        ps.setLong(4, edge.getToNode().getId());
                        ps.setDouble(5, edge.getLengthM());
                        ps.setBoolean(6, Boolean.TRUE.equals(edge.getWalkAllowed()));
                        ps.setBoolean(7, Boolean.TRUE.equals(edge.getCarAllowed()));
                        ps.setBoolean(8, Boolean.TRUE.equals(edge.getMixedAllowed()));
                        ps.setBoolean(9, Boolean.TRUE.equals(edge.getPublicTransportAllowed()));
                        setNullableInteger(ps, 10, edge.getWalkTimeSec());
                        setNullableInteger(ps, 11, edge.getCarTimeSec());
                        setNullableInteger(ps, 12, edge.getMixedTimeSec());
                        setNullableInteger(ps, 13, edge.getPublicTransportTimeSec());
                        ps.setBoolean(14, Boolean.TRUE.equals(edge.getBidirectional()));
                        ps.setString(15, edge.getGeomWkt());
                        ps.setString(16, edge.getPolylineJson());
                        ps.setString(17, edge.getSource());
                    }

                    @Override
                    public int getBatchSize() {
                        return filteredEdges.size();
                    }
                }
        );
    }

    public void persistBindings(List<PoiGraphBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO poi_graph_bindings (
                    poi_id,
                    city_id,
                    graph_version_id,
                    nearest_node_id,
                    snapped_latitude,
                    snapped_longitude,
                    snap_distance_m,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, now(), now())
                    ON CONFLICT (poi_id, graph_version_id) DO UPDATE SET
                        city_id = EXCLUDED.city_id,
                        nearest_node_id = EXCLUDED.nearest_node_id,
                        snapped_latitude = EXCLUDED.snapped_latitude,
                        snapped_longitude = EXCLUDED.snapped_longitude,
                        snap_distance_m = EXCLUDED.snap_distance_m,
                        updated_at = now()
                """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        PoiGraphBinding binding = bindings.get(i);
                        ps.setLong(1, binding.getPoiId());
                        ps.setLong(2, binding.getCityId());
                        ps.setLong(3, binding.getGraphVersion().getId());
                        ps.setLong(4, binding.getNearestNode().getId());
                        ps.setDouble(5, binding.getSnappedLatitude());
                        ps.setDouble(6, binding.getSnappedLongitude());
                        ps.setDouble(7, binding.getSnapDistanceM());
                    }

                    @Override
                    public int getBatchSize() {
                        return bindings.size();
                    }
                }
        );
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
            return;
        }
        ps.setInt(index, value);
    }
}
