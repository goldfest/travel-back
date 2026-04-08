package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoadNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoadNodeRepository extends JpaRepository<RoadNode, Long> {

    List<RoadNode> findByCityIdAndGraphVersionId(Long cityId, Long graphVersionId);

    @Query(value = """
            SELECT rn.*
            FROM road_nodes rn
            WHERE rn.city_id = :cityId
              AND rn.graph_version_id = :graphVersionId
            ORDER BY rn.geom <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
            LIMIT 1
            """, nativeQuery = true)
    Optional<RoadNode> findNearestNode(
            @Param("cityId") Long cityId,
            @Param("graphVersionId") Long graphVersionId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );

    @Query(value = """
            SELECT rn.*
            FROM road_nodes rn
            WHERE rn.city_id = :cityId
              AND rn.graph_version_id = :graphVersionId
              AND EXISTS (
                  SELECT 1
                  FROM road_edges re
                  WHERE re.graph_version_id = rn.graph_version_id
                    AND (re.from_node_id = rn.id OR re.to_node_id = rn.id)
                    AND re.car_allowed = true
              )
            ORDER BY rn.geom <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
            LIMIT 1
            """, nativeQuery = true)
    Optional<RoadNode> findNearestCarNode(
            @Param("cityId") Long cityId,
            @Param("graphVersionId") Long graphVersionId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );
}
