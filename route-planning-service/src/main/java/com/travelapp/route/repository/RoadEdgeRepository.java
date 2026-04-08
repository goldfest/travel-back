package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoadEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoadEdgeRepository extends JpaRepository<RoadEdge, Long> {

    @Query(value = """
            SELECT
                re.id AS edgeId,
                re.from_node_id AS fromNodeId,
                fn.latitude AS fromLatitude,
                fn.longitude AS fromLongitude,
                re.to_node_id AS toNodeId,
                tn.latitude AS toLatitude,
                tn.longitude AS toLongitude,
                re.length_m AS lengthM,
                re.walk_allowed AS walkAllowed,
                re.car_allowed AS carAllowed,
                re.mixed_allowed AS mixedAllowed,
                re.public_transport_allowed AS publicTransportAllowed,
                re.walk_time_sec AS walkTimeSec,
                re.car_time_sec AS carTimeSec,
                re.mixed_time_sec AS mixedTimeSec,
                re.public_transport_time_sec AS publicTransportTimeSec,
                re.bidirectional AS bidirectional,
                re.polyline_json AS polylineJson
            FROM road_edges re
            JOIN road_nodes fn ON fn.id = re.from_node_id
            JOIN road_nodes tn ON tn.id = re.to_node_id
            WHERE re.graph_version_id = :graphVersionId
            """, nativeQuery = true)
    List<RoadEdgeProjection> findProjectedByGraphVersionId(@Param("graphVersionId") Long graphVersionId);
}
