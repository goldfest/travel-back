package com.travelapp.review.repository;

import com.travelapp.review.model.entity.ReportMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportMediaRepository extends JpaRepository<ReportMedia, Long> {

    List<ReportMedia> findByReportId(Long reportId);
}
