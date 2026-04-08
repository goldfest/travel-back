package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RouteSegmentPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteSegmentPathRepository extends JpaRepository<RouteSegmentPath, Long> {
    List<RouteSegmentPath> findByRouteDayIdOrderBySegmentOrderAsc(Long routeDayId);
    void deleteByRouteDayId(Long routeDayId);
}