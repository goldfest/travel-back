package com.travelapp.route.repository;

import com.travelapp.route.model.entity.Route;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    Page<Route> findByUserIdAndStatusNotOrderByUpdatedAtDesc(
            Long userId,
            Route.RouteStatus status,
            Pageable pageable
    );

    Page<Route> findByUserIdAndStatusOrderByUpdatedAtDesc(
            Long userId,
            Route.RouteStatus status,
            Pageable pageable
    );

    List<Route> findByUserIdAndCityIdAndStatusNotOrderByUpdatedAtDesc(
            Long userId,
            Long cityId,
            Route.RouteStatus status
    );

    Optional<Route> findByUserIdAndId(Long userId, Long id);

    long countByUserIdAndStatusNot(Long userId, Route.RouteStatus status);

    List<Route> findByIsOptimizedTrueAndStatusNot(Route.RouteStatus status);

    boolean existsByUserIdAndNameAndStatusNot(Long userId, String name, Route.RouteStatus status);

    boolean existsByUserIdAndNameAndStatus(Long userId, String name, Route.RouteStatus status);

    Optional<Route> findByIdAndUserId(Long id, Long userId);

    @Query("""
        select distinct r
        from Route r
        left join fetch r.routeDays d
        left join fetch d.routePoints p
        where r.id = :id and r.userId = :userId
    """)
    Optional<Route> findFullByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
        select distinct r
        from Route r
        left join fetch r.routeDays d
        left join fetch d.routePoints p
        where r.id = :id
    """)
    Optional<Route> findFullById(@Param("id") Long id);
}