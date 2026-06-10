package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.entity.PoiGraphBinding;
import com.travelapp.graphimport.model.entity.RoadEdge;
import com.travelapp.graphimport.model.entity.RoadNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportPersistenceService {

    private static final String ROAD_NODES_SEQUENCE = "road_nodes_id_seq";

    private final JdbcTemplate jdbcTemplate;

    @Value("${graph-import.batch-size:1000}")
    private int batchSize;

    /**
     * Метод оставлен для совместимости. Массовый импорт теперь выполняется без длинной транзакции,
     * поэтому SET LOCAL здесь не используется.
     */
    public void configureImportSession() {
        // no-op
    }

    public void persistNodes(Collection<RoadNode> nodes) {
        persistNodes(nodes, null);
    }

    public void persistNodes(Collection<RoadNode> nodes, BiConsumer<Integer, Integer> progressCallback) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        List<RoadNode> nodeList = new ArrayList<>(nodes);
        String sql = """
                INSERT INTO road_nodes (
                    id,
                    city_id,
                    graph_version_id,
                    latitude,
                    longitude,
                    geom
                ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                """;

        int total = nodeList.size();
        int effectiveBatchSize = effectiveBatchSize();
        int saved = 0;
        long startedAt = System.currentTimeMillis();

        for (int from = 0; from < total; from += effectiveBatchSize) {
            int to = Math.min(from + effectiveBatchSize, total);
            List<RoadNode> batch = nodeList.subList(from, to);
            assignNodeIds(batch);

            log.info("Graph import nodes batch started: from={} to={} total={} batchSize={}",
                    from + 1, to, total, batch.size());

            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            RoadNode node = batch.get(i);
                            ps.setLong(1, node.getId());
                            ps.setLong(2, node.getCityId());
                            ps.setLong(3, node.getGraphVersion().getId());
                            ps.setDouble(4, node.getLatitude());
                            ps.setDouble(5, node.getLongitude());
                            ps.setDouble(6, node.getLongitude());
                            ps.setDouble(7, node.getLatitude());
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    }
            );

            saved = to;
            publishProgress(progressCallback, saved, total);
            log.info("Graph import nodes persisted: saved={}/{} batchSize={} elapsedMs={}",
                    saved, total, batch.size(), System.currentTimeMillis() - startedAt);
        }
    }

    public void persistEdges(List<RoadEdge> edges) {
        persistEdges(edges, null);
    }

    public void persistEdges(List<RoadEdge> edges, BiConsumer<Integer, Integer> progressCallback) {
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

        String sql = """
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
                """;

        int total = filteredEdges.size();
        int effectiveBatchSize = effectiveBatchSize();
        int saved = 0;
        long startedAt = System.currentTimeMillis();

        for (int from = 0; from < total; from += effectiveBatchSize) {
            int to = Math.min(from + effectiveBatchSize, total);
            List<RoadEdge> batch = filteredEdges.subList(from, to);

            log.info("Graph import edges batch started: from={} to={} total={} batchSize={}",
                    from + 1, to, total, batch.size());

            jdbcTemplate.batchUpdate(
                    sql,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            RoadEdge edge = batch.get(i);
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
                            return batch.size();
                        }
                    }
            );

            saved = to;
            publishProgress(progressCallback, saved, total);
            log.info("Graph import edges persisted: saved={}/{} batchSize={} elapsedMs={}",
                    saved, total, batch.size(), System.currentTimeMillis() - startedAt);
        }
    }


    public void deleteGraphData(Long cityId, Long graphVersionId) {
        jdbcTemplate.update(
                "DELETE FROM poi_graph_bindings WHERE city_id = ? AND graph_version_id = ?",
                cityId,
                graphVersionId
        );
        jdbcTemplate.update(
                "DELETE FROM road_edges WHERE city_id = ? AND graph_version_id = ?",
                cityId,
                graphVersionId
        );
        jdbcTemplate.update(
                "DELETE FROM road_nodes WHERE city_id = ? AND graph_version_id = ?",
                cityId,
                graphVersionId
        );
        log.info("Graph import partial data cleaned: cityId={}, versionId={}", cityId, graphVersionId);
    }

    public void deleteBindings(Long cityId, Long graphVersionId) {
        jdbcTemplate.update(
                "DELETE FROM poi_graph_bindings WHERE city_id = ? AND graph_version_id = ?",
                cityId,
                graphVersionId
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

        log.info("Graph import POI bindings persisted: count={}", bindings.size());
    }

    private void assignNodeIds(List<RoadNode> batch) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT nextval('" + ROAD_NODES_SEQUENCE + "') FROM generate_series(1, ?)",
                Long.class,
                batch.size()
        );

        if (ids.size() != batch.size()) {
            throw new IllegalStateException("PostgreSQL вернул некорректное количество id для road_nodes: expected="
                    + batch.size() + ", actual=" + ids.size());
        }

        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).setId(ids.get(i));
        }
    }

    private int effectiveBatchSize() {
        return Math.max(100, batchSize);
    }

    private void publishProgress(BiConsumer<Integer, Integer> progressCallback, int saved, int total) {
        if (progressCallback != null) {
            progressCallback.accept(saved, total);
        }
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
            return;
        }
        ps.setInt(index, value);
    }
}
