package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RouteDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteDayRepository extends JpaRepository<RouteDay, Long> {

    List<RouteDay> findByRouteIdOrderByDayNumberAsc(Long routeId);

    Optional<RouteDay> findByRouteIdAndDayNumber(Long routeId, Short dayNumber);

    @Query("SELECT MAX(rd.dayNumber) FROM RouteDay rd WHERE rd.route.id = :routeId")
    Optional<Short> findMaxDayNumberByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT rd FROM RouteDay rd WHERE rd.route.id = :routeId AND rd.route.userId = :userId")
    Optional<RouteDay> findByRouteIdAndUserId(@Param("routeId") Long routeId, @Param("userId") Long userId);

    void deleteByRouteId(Long routeId);
}