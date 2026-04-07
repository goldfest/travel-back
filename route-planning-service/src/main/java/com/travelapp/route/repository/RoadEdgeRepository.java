package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoadEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadEdgeRepository extends JpaRepository<RoadEdge, Long> {
    List<RoadEdge> findByCityId(Long cityId);
}
