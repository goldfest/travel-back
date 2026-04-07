package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoadNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoadNodeRepository extends JpaRepository<RoadNode, Long> {

    List<RoadNode> findByCityId(Long cityId);

    @Query(value = """
            SELECT rn.*
            FROM road_nodes rn
            WHERE rn.city_id = :cityId
            ORDER BY ((rn.latitude - :latitude) * (rn.latitude - :latitude)
                   +  (rn.longitude - :longitude) * (rn.longitude - :longitude)) ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RoadNode> findNearestNode(
            @Param("cityId") Long cityId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude
    );
}
