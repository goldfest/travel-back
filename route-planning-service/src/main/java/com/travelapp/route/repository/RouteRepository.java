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

    @Query("SELECT r FROM Route r WHERE r.userId = :userId AND r.isArchived = 0 ORDER BY r.updatedAt DESC")
    Page<Route> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM Route r WHERE r.userId = :userId AND r.isArchived = 1 ORDER BY r.updatedAt DESC")
    Page<Route> findArchivedByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM Route r WHERE r.userId = :userId AND r.cityId = :cityId AND r.isArchived = 0")
    List<Route> findByUserIdAndCityId(@Param("userId") Long userId, @Param("cityId") Long cityId);

    @Query("SELECT r FROM Route r WHERE r.userId = :userId AND r.id = :id")
    Optional<Route> findByUserIdAndId(@Param("userId") Long userId, @Param("id") Long id);

    @Query("SELECT COUNT(r) FROM Route r WHERE r.userId = :userId AND r.isArchived = 0")
    long countActiveRoutesByUserId(@Param("userId") Long userId);

    @Query("SELECT r FROM Route r WHERE r.isOptimized = true AND r.isArchived = 0")
    List<Route> findAllOptimizedRoutes();

    boolean existsByUserIdAndNameAndIsArchived(Long userId, String name, Short isArchived);
}