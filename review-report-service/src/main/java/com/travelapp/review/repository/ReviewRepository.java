package com.travelapp.review.repository;

import com.travelapp.review.model.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByPoiId(Long poiId);

    Page<Review> findByPoiId(Long poiId, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    Optional<Review> findByPoiIdAndUserId(Long poiId, Long userId);

    List<Review> findByPoiIdAndIsHiddenFalse(Long poiId);

    Page<Review> findByPoiIdAndIsHiddenFalse(Long poiId, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.poiId = :poiId AND r.rating = :rating")
    Page<Review> findByPoiIdAndRating(@Param("poiId") Long poiId,
                                      @Param("rating") Short rating,
                                      Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.poiId = :poiId AND r.isHidden = false")
    Double calculateAverageRating(@Param("poiId") Long poiId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.poiId = :poiId AND r.isHidden = false")
    Long countVisibleReviews(@Param("poiId") Long poiId);

    @Query("SELECT r FROM Review r WHERE r.userId = :userId AND r.isHidden = false")
    Page<Review> findVisibleByUserId(@Param("userId") Long userId, Pageable pageable);

    boolean existsByPoiIdAndUserId(Long poiId, Long userId);
}