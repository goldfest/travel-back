package com.travelapp.review.repository;

import com.travelapp.review.model.entity.ReviewMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, Long> {

    List<ReviewMedia> findByReviewId(Long reviewId);

    @Modifying
    @Query("DELETE FROM ReviewMedia rm WHERE rm.review.id = :reviewId")
    void deleteByReviewId(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("DELETE FROM ReviewMedia rm WHERE rm.review.id = :reviewId AND rm.id IN :ids")
    void deleteByReviewIdAndIds(@Param("reviewId") Long reviewId, @Param("ids") List<Long> ids);
}