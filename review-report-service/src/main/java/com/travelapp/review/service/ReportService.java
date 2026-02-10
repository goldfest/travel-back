package com.travelapp.review.service;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportService {

    ReportResponse createReport(Long userId, CreateReportRequest request);

    ReportResponse getReportById(Long id);

    Page<ReportResponse> getAllReports(Pageable pageable);

    Page<ReportResponse> getReportsByStatus(String status, Pageable pageable);

    Page<ReportResponse> getReportsByUserId(Long userId, Pageable pageable);

    Page<ReportResponse> getReportsByModeratorId(Long moderatorId, Pageable pageable);

    ReportResponse updateReport(Long id, Long moderatorId, UpdateReportRequest request);

    void deleteReport(Long id, Long moderatorId);

    Long getPendingReportsCount();

    void processReport(Long id, Long moderatorId, String status, String comment);
}