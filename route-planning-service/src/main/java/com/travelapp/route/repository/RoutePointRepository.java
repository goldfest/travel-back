package com.travelapp.route.repository;

import com.travelapp.route.model.entity.RoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutePointRepository extends JpaRepository<RoutePoint, Long> {

    List<RoutePoint> findByRouteDayIdOrderByOrderIndexAsc(Long routeDayId);

    List<RoutePoint> findByPoiId(Long poiId);

    @Query("SELECT rp FROM RoutePoint rp WHERE rp.routeDay.route.id = :routeId")
    List<RoutePoint> findByRouteId(@Param("routeId") Long routeId);

    @Query("SELECT rp FROM RoutePoint rp WHERE rp.routeDay.id = :routeDayId AND rp.poiId = :poiId")
    Optional<RoutePoint> findByRouteDayIdAndPoiId(@Param("routeDayId") Long routeDayId, @Param("poiId") Long poiId);

    @Query("SELECT MAX(rp.orderIndex) FROM RoutePoint rp WHERE rp.routeDay.id = :routeDayId")
    Optional<Short> findMaxOrderIndexByRouteDayId(@Param("routeDayId") Long routeDayId);

    @Modifying
    @Query("DELETE FROM RoutePoint rp WHERE rp.routeDay.route.id = :routeId")
    void deleteByRouteId(@Param("routeId") Long routeId);

    @Modifying
    @Query("DELETE FROM RoutePoint rp WHERE rp.routeDay.id = :routeDayId")
    void deleteByRouteDayId(@Param("routeDayId") Long routeDayId);

    boolean existsByRouteDayIdAndPoiId(Long routeDayId, Long poiId);
}