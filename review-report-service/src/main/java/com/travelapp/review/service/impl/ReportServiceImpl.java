package com.travelapp.review.service.impl;

import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReportMapper;
import com.travelapp.review.model.dto.InternalUserResponse;
import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.model.entity.Report;
import com.travelapp.review.model.entity.ReportMedia;
import com.travelapp.review.repository.ReportRepository;
import com.travelapp.review.repository.ReviewRepository;
import com.travelapp.review.service.AuthUserService;
import com.travelapp.review.service.ReportService;
import com.travelapp.review.service.media.ReviewMediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final ReportMapper reportMapper;
    private final PoiClient poiClient;
    private final AuthUserService authUserService;
    private final ReviewMediaStorageService mediaStorageService;

    @Override
    @Transactional
    public ReportResponse createReport(Long userId, CreateReportRequest request) {
        Report saved = createReportEntity(userId, request);
        return enrich(saved);
    }

    @Override
    @Transactional
    public ReportResponse createReportWithMedia(Long userId, CreateReportRequest request, List<MultipartFile> files) {
        Report saved = createReportEntity(userId, request);

        List<ReportMedia> media = mediaStorageService.createReportMedia(saved, userId, files);
        for (ReportMedia mediaItem : media) {
            saved.addMedia(mediaItem);
        }

        if ((saved.getPhotoUrl() == null || saved.getPhotoUrl().isBlank()) && !media.isEmpty()) {
            saved.setPhotoUrl(media.get(0).getFileUrl());
        }

        saved = reportRepository.save(saved);
        return enrich(saved);
    }

    @Override
    @Transactional
    public ReportResponse addMediaToReport(Long id, Long userId, List<MultipartFile> files) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        if (!report.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to update this report");
        }

        if (!isPending(report.getStatus())) {
            throw new IllegalArgumentException("Only pending report can be updated");
        }

        List<ReportMedia> media = mediaStorageService.createReportMedia(report, userId, files);
        for (ReportMedia mediaItem : media) {
            report.addMedia(mediaItem);
        }

        if ((report.getPhotoUrl() == null || report.getPhotoUrl().isBlank()) && !media.isEmpty()) {
            report.setPhotoUrl(media.get(0).getFileUrl());
        }

        Report saved = reportRepository.save(report);
        return enrich(saved);
    }

    private Report createReportEntity(Long userId, CreateReportRequest request) {
        validateCreateRequest(request);

        if (request.getPoiId() != null) {
            boolean poiExists = poiClient.checkPoiExists(request.getPoiId());
            if (!poiExists) {
                throw new ResourceNotFoundException("POI not found with id: " + request.getPoiId());
            }

            try {
                poiClient.reportPoi(request.getPoiId(), null);
            } catch (Exception e) {
                log.warn("Failed to notify poi-service about report for poiId={}", request.getPoiId());
            }
        }

        if (request.getReviewId() != null && !reviewRepository.existsById(request.getReviewId())) {
            throw new ResourceNotFoundException("Review not found with id: " + request.getReviewId());
        }

        Report report = reportMapper.toEntity(request);
        report.setUserId(userId);

        if (report.getStatus() == null || report.getStatus().isBlank()) {
            report.setStatus("pending");
        }

        return reportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByUserId(Long userId, Pageable pageable) {
        Pageable safePageable = sanitizeReportPageable(pageable);

        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId, safePageable)
                .map(this::enrich);
    }

    @Override
    @Transactional
    public ReportResponse updateReport(Long id, Long userId, UpdateReportRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        if (!report.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to update this report");
        }

        if (!isPending(report.getStatus())) {
            throw new IllegalArgumentException("Only pending report can be updated");
        }

        if (request.getComment() != null) {
            report.setComment(request.getComment());
        }

        if (request.getPhotoUrl() != null) {
            report.setPhotoUrl(request.getPhotoUrl());
        }

        Report saved = reportRepository.save(report);
        return enrich(saved);
    }

    @Override
    @Transactional
    public void deleteReport(Long id, Long userId) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        if (!report.getUserId().equals(userId)) {
            throw new SecurityException("User is not authorized to delete this report");
        }

        if (!isPending(report.getStatus())) {
            throw new IllegalArgumentException("Only pending report can be deleted");
        }

        reportRepository.delete(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getAllReports(Pageable pageable) {
        Pageable safePageable = sanitizeReportPageable(pageable);

        return reportRepository.findAllByOrderByCreatedAtDesc(safePageable)
                .map(this::enrich);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByStatus(String status, Pageable pageable) {
        Pageable safePageable = sanitizeReportPageable(pageable);

        return reportRepository.findByStatusOrderByCreatedAtDesc(status, safePageable)
                .map(this::enrich);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByModeratorId(Long moderatorId, Pageable pageable) {
        Pageable safePageable = sanitizeReportPageable(pageable);

        return reportRepository.findByHandledByUserIdOrderByCreatedAtDesc(moderatorId, safePageable)
                .map(this::enrich);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getPendingReportsCount() {
        return reportRepository.countByStatus("pending");
    }

    @Override
    @Transactional
    public ReportResponse processReport(Long id, Long moderatorId, String status, String moderatorComment) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        String normalizedStatus = normalizeModerationStatus(status);

        report.setStatus(normalizedStatus);
        report.setHandledByUserId(moderatorId);
        report.setHandledAt(java.time.LocalDateTime.now());

        if (moderatorComment != null && !moderatorComment.isBlank()) {
            report.setModeratorComment(moderatorComment);
        }

        Report saved = reportRepository.save(report);
        return enrich(saved);
    }

    private void validateCreateRequest(CreateReportRequest request) {
        boolean hasReview = request.getReviewId() != null;
        boolean hasPoi = request.getPoiId() != null;

        if (hasReview == hasPoi) {
            throw new IllegalArgumentException("Report must target either reviewId or poiId");
        }
    }

    private String normalizeModerationStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        String normalized = status.trim().toLowerCase();

        if (!normalized.equals("approved") && !normalized.equals("rejected")) {
            throw new IllegalArgumentException("Allowed statuses: approved, rejected");
        }

        return normalized;
    }

    private boolean isPending(String status) {
        return status != null && status.equalsIgnoreCase("pending");
    }

    private ReportResponse enrich(Report report) {
        ReportResponse response = reportMapper.toResponse(report);

        response.setUserName(resolveUserName(report.getUserId(), false));
        response.setUserAvatarUrl(resolveUserAvatar(report.getUserId()));

        if (report.getHandledByUserId() != null) {
            response.setHandledByUserName(resolveUserName(report.getHandledByUserId(), true));
            response.setHandledByUserAvatarUrl(resolveUserAvatar(report.getHandledByUserId()));
        }

        return response;
    }

    private String resolveUserName(Long userId, boolean moderator) {
        try {
            InternalUserResponse user = authUserService.getUserInfo(userId);
            if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
                return user.getUsername();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve username for userId={}", userId);
        }

        return moderator ? "Moderator_" + userId : "User_" + userId;
    }

    private String resolveUserAvatar(Long userId) {
        try {
            InternalUserResponse user = authUserService.getUserInfo(userId);
            return user != null ? user.getAvatarUrl() : null;
        } catch (Exception e) {
            log.warn("Failed to resolve avatar for userId={}", userId);
            return null;
        }
    }

    private Pageable sanitizeReportPageable(Pageable pageable) {
        Set<String> allowedSortFields = Set.of(
                "id",
                "reportType",
                "status",
                "createdAt",
                "handledAt",
                "userId",
                "handledByUserId",
                "reviewId",
                "poiId"
        );

        Sort safeSort = Sort.by(
                pageable.getSort().stream()
                        .filter(order -> allowedSortFields.contains(order.getProperty()))
                        .map(order -> new Sort.Order(order.getDirection(), order.getProperty()))
                        .toList()
        );

        if (safeSort.isUnsorted()) {
            safeSort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                safeSort
        );
    }
}