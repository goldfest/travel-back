package com.travelapp.review.repository;

import com.travelapp.review.model.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByUserIdAndReviewId(Long userId, Long reviewId);

    boolean existsByUserIdAndReviewId(Long userId, Long reviewId);

    @Query("SELECT COUNT(rl) FROM ReviewLike rl WHERE rl.review.id = :reviewId")
    Long countByReviewId(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("DELETE FROM ReviewLike rl WHERE rl.review.id = :reviewId AND rl.userId = :userId")
    void deleteByReviewIdAndUserId(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM ReviewLike rl WHERE rl.review.id = :reviewId")
    void deleteByReviewId(@Param("reviewId") Long reviewId);
}