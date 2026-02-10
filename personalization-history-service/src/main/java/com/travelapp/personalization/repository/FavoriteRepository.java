package com.travelapp.personalization.repository;

import com.travelapp.personalization.model.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndPoiId(Long userId, Long poiId);

    boolean existsByUserIdAndPoiId(Long userId, Long poiId);

    Page<Favorite> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.userId = :userId")
    long countByUserId(@Param("userId") Long userId);

    @Query("SELECT f.poiId FROM Favorite f WHERE f.userId = :userId ORDER BY f.createdAt DESC")
    List<Long> findPoiIdsByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndPoiId(Long userId, Long poiId);

    void deleteByUserId(Long userId);

    void deleteByPoiId(Long poiId);
}