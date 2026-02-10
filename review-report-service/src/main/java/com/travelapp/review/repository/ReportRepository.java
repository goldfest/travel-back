package com.travelapp.review.repository;

import com.travelapp.review.model.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findByStatus(String status, Pageable pageable);

    Page<Report> findByUserId(Long userId, Pageable pageable);

    List<Report> findByReviewId(Long reviewId);

    List<Report> findByPoiId(Long poiId);

    @Query("SELECT r FROM Report r WHERE r.reviewId = :reviewId AND r.status = 'pending'")
    List<Report> findPendingByReviewId(@Param("reviewId") Long reviewId);

    @Query("SELECT r FROM Report r WHERE r.poiId = :poiId AND r.status = 'pending'")
    List<Report> findPendingByPoiId(@Param("poiId") Long poiId);

    @Query("SELECT COUNT(r) FROM Report r WHERE r.status = 'pending'")
    Long countPendingReports();

    @Query("SELECT r FROM Report r WHERE r.handledByUserId = :moderatorId")
    Page<Report> findByModeratorId(@Param("moderatorId") Long moderatorId, Pageable pageable);

    @Query("SELECT r FROM Report r WHERE r.createdAt BETWEEN :startDate AND :endDate")
    Page<Report> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate,
                                 Pageable pageable);
}