package com.travelapp.graphimport.repository;

import com.travelapp.graphimport.model.entity.RoadNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoadNodeRepository extends JpaRepository<RoadNode, Long> {

    @Modifying
    @Query("delete from RoadNode rn where rn.cityId = :cityId and rn.graphVersion.id = :graphVersionId")
    void deleteByCityIdAndGraphVersionId(@Param("cityId") Long cityId, @Param("graphVersionId") Long graphVersionId);

    @Query(value = """
            SELECT rn.*
            FROM road_nodes rn
            WHERE rn.city_id = :cityId
              AND rn.graph_version_id = :graphVersionId
            ORDER BY rn.geom <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
            LIMIT 1
            """, nativeQuery = true)
    Optional<RoadNode> findNearestNode(@Param("cityId") Long cityId,
                                       @Param("graphVersionId") Long graphVersionId,
                                       @Param("latitude") double latitude,
                                       @Param("longitude") double longitude);
}
