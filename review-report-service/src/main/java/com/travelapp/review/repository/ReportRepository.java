package com.travelapp.review.repository;

import com.travelapp.review.model.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Page<Report> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Report> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<Report> findByHandledByUserIdOrderByCreatedAtDesc(Long moderatorId, Pageable pageable);

    Long countByStatus(String status);
}