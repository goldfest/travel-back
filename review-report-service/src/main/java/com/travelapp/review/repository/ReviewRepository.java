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

    List<Review> findByPoiIdAndIsHiddenFalseAndModerationStatus(Long poiId, Review.ModerationStatus moderationStatus);

    Page<Review> findByPoiIdAndIsHiddenFalseAndModerationStatus(Long poiId,
                                                                Review.ModerationStatus moderationStatus,
                                                                Pageable pageable);

    @Query("""
       SELECT r FROM Review r
       WHERE r.moderationStatus = :moderationStatus
       ORDER BY r.createdAt ASC
       """)
    Page<Review> findPendingReviews(
            @Param("moderationStatus") Review.ModerationStatus moderationStatus,
            Pageable pageable
    );

    @Query("SELECT r FROM Review r WHERE r.poiId = :poiId AND r.rating = :rating AND r.isHidden = false AND r.moderationStatus = :status")
    Page<Review> findByPoiIdAndRating(@Param("poiId") Long poiId,
                                      @Param("rating") Short rating,
                                      @Param("status") Review.ModerationStatus status,
                                      Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.poiId = :poiId AND r.isHidden = false AND r.moderationStatus = :status")
    Double calculateAverageRating(@Param("poiId") Long poiId,
                                  @Param("status") Review.ModerationStatus status);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.poiId = :poiId AND r.isHidden = false AND r.moderationStatus = :status")
    Long countVisibleReviews(@Param("poiId") Long poiId,
                             @Param("status") Review.ModerationStatus status);

    @Query("SELECT r FROM Review r WHERE r.userId = :userId AND r.isHidden = false AND r.moderationStatus = :status")
    Page<Review> findVisibleByUserId(@Param("userId") Long userId,
                                     @Param("status") Review.ModerationStatus status,
                                     Pageable pageable);

    boolean existsByPoiIdAndUserId(Long poiId, Long userId);
}