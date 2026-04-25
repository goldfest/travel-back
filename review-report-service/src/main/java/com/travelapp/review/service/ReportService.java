package com.travelapp.review.service;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReportService {

    ReportResponse createReport(Long userId, CreateReportRequest request);

    ReportResponse createReportWithMedia(Long userId, CreateReportRequest request, List<MultipartFile> files);

    ReportResponse addMediaToReport(Long id, Long userId, List<MultipartFile> files);

    Page<ReportResponse> getReportsByUserId(Long userId, Pageable pageable);

    ReportResponse updateReport(Long id, Long userId, UpdateReportRequest request);

    void deleteReport(Long id, Long userId);

    Page<ReportResponse> getAllReports(Pageable pageable);

    Page<ReportResponse> getReportsByStatus(String status, Pageable pageable);

    Page<ReportResponse> getReportsByModeratorId(Long moderatorId, Pageable pageable);

    Long getPendingReportsCount();

    ReportResponse processReport(Long id, Long moderatorId, String status, String moderatorComment);
}