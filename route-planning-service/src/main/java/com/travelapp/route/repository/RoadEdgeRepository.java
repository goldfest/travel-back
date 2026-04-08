package com.travelapp.route.repository;

import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.model.entity.RoadEdge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadEdgeRepository extends JpaRepository<RoadEdge, Long> {

    @EntityGraph(attributePaths = {"fromNode", "toNode"})
    List<RoadEdge> findByGraphVersion(CityGraphVersion graphVersion);

    @EntityGraph(attributePaths = {"fromNode", "toNode"})
    List<RoadEdge> findByGraphVersion_Id(Long graphVersionId);
}