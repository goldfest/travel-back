package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoadNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoadNodeRepository extends JpaRepository<RoadNode, Long> {

    @Query(value = """
            SELECT rn.*
            FROM road_nodes rn
            WHERE rn.city_id = :cityId
            ORDER BY rn.geom <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
            LIMIT 1
            """, nativeQuery = true)
    Optional<RoadNode> findNearestNode(
            @Param("cityId") Long cityId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );
}
