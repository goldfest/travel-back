package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RouteDayPath;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteDayPathRepository extends JpaRepository<RouteDayPath, Long> {

    Optional<RouteDayPath> findByRouteDayId(Long routeDayId);

    void deleteByRouteDayId(Long routeDayId);
}