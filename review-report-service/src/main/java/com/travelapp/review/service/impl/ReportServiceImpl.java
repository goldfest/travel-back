package com.travelapp.review.service.impl;

import com.travelapp.review.client.NotificationClient;
import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReportMapper;
import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.model.entity.Report;
import com.travelapp.review.repository.ReportRepository;
import com.travelapp.review.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final NotificationClient notificationClient;
    private final PoiClient poiClient;

    @Override
    @Transactional
    public ReportResponse createReport(Long userId, CreateReportRequest request) {
        log.info("Creating report by user: {} with type: {}", userId, request.getReportType());

        // Валидация цели отчета
        if ((request.getReviewId() == null && request.getPoiId() == null) ||
                (request.getReviewId() != null && request.getPoiId() != null)) {
            throw new IllegalArgumentException("Report must target either a review or a POI");
        }

        // Проверяем существование цели (в реальном проекте через вызовы других сервисов)
        if (request.getReviewId() != null) {
            // Проверка существования отзыва
            // reviewClient.checkReviewExists(request.getReviewId());
        } else if (request.getPoiId() != null) {
            boolean poiExists = poiClient.checkPoiExists(request.getPoiId());
            if (!poiExists) {
                throw new ResourceNotFoundException("POI not found with id: " + request.getPoiId());
            }
        }

        Report report = reportMapper.toEntity(request);
        report.setUserId(userId);

        Report savedReport = reportRepository.save(report);

        log.info("Report created with id: {}", savedReport.getId());

        // Отправляем уведомление о новом отчете (для модераторов)
        sendNewReportNotification(savedReport);

        return reportMapper.toResponse(savedReport);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponse getReportById(Long id) {
        log.info("Fetching report by id: {}", id);

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        // Получаем дополнительную информацию о цели отчета
        String targetTitle = getTargetTitle(report);
        String handledByUserName = report.getHandledByUserId() != null ?
                "Moderator_" + report.getHandledByUserId() : null;

        return reportMapper.toResponseWithDetails(report, handledByUserName, targetTitle);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getAllReports(Pageable pageable) {
        log.info("Fetching all reports with page: {}", pageable);

        Page<Report> reports = reportRepository.findAll(pageable);

        return reports.map(report -> {
            String targetTitle = getTargetTitle(report);
            String handledByUserName = report.getHandledByUserId() != null ?
                    "Moderator_" + report.getHandledByUserId() : null;

            return reportMapper.toResponseWithDetails(report, handledByUserName, targetTitle);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByStatus(String status, Pageable pageable) {
        log.info("Fetching reports with status: {} and page: {}", status, pageable);

        validateStatus(status);

        Page<Report> reports = reportRepository.findByStatus(status, pageable);

        return reports.map(report -> {
            String targetTitle = getTargetTitle(report);
            return reportMapper.toResponseWithDetails(report, null, targetTitle);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByUserId(Long userId, Pageable pageable) {
        log.info("Fetching reports by user: {} with page: {}", userId, pageable);

        Page<Report> reports = reportRepository.findByUserId(userId, pageable);

        return reports.map(report -> {
            String targetTitle = getTargetTitle(report);
            String handledByUserName = report.getHandledByUserId() != null ?
                    "Moderator_" + report.getHandledByUserId() : null;

            return reportMapper.toResponseWithDetails(report, handledByUserName, targetTitle);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> getReportsByModeratorId(Long moderatorId, Pageable pageable) {
        log.info("Fetching reports handled by moderator: {} with page: {}", moderatorId, pageable);

        Page<Report> reports = reportRepository.findByModeratorId(moderatorId, pageable);

        return reports.map(report -> {
            String targetTitle = getTargetTitle(report);
            return reportMapper.toResponseWithDetails(report, "Moderator_" + moderatorId, targetTitle);
        });
    }

    @Override
    @Transactional
    @CacheEvict(value = "pendingReportsCount", allEntries = true)
    public ReportResponse updateReport(Long id, Long moderatorId, UpdateReportRequest request) {
        log.info("Updating report: {} by moderator: {}", id, moderatorId);

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        if (request.getStatus() != null) {
            validateStatus(request.getStatus());

            report.setStatus(request.getStatus());
            report.setHandledByUserId(moderatorId);
            report.setHandledAt(LocalDateTime.now());

            // Если жалоба одобрена, принимаем меры (скрываем отзыв и т.д.)
            if ("approved".equals(request.getStatus())) {
                handleApprovedReport(report);
            }

            // Отправляем уведомление пользователю о результате модерации
            sendReportResolutionNotification(report, request.getModeratorComment());
        }

        Report updatedReport = reportRepository.save(report);

        log.info("Report updated: {}", id);

        String targetTitle = getTargetTitle(updatedReport);
        String handledByUserName = "Moderator_" + moderatorId;

        return reportMapper.toResponseWithDetails(updatedReport, handledByUserName, targetTitle);
    }

    @Override
    @Transactional
    @CacheEvict(value = "pendingReportsCount", allEntries = true)
    public void deleteReport(Long id, Long moderatorId) {
        log.info("Deleting report: {} by moderator: {}", id, moderatorId);

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + id));

        reportRepository.delete(report);

        log.info("Report deleted: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "pendingReportsCount")
    public Long getPendingReportsCount() {
        log.info("Fetching pending reports count");
        return reportRepository.countPendingReports();
    }

    @Override
    @Transactional
    @CacheEvict(value = "pendingReportsCount", allEntries = true)
    public void processReport(Long id, Long moderatorId, String status, String comment) {
        log.info("Processing report: {} by moderator: {} with status: {}", id, moderatorId, status);

        UpdateReportRequest request = UpdateReportRequest.builder()
                .status(status)
                .moderatorComment(comment)
                .build();

        updateReport(id, moderatorId, request);
    }

    private void validateStatus(String status) {
        if (!"pending".equals(status) && !"approved".equals(status) && !"rejected".equals(status)) {
            throw new IllegalArgumentException("Invalid report status: " + status);
        }
    }

    private String getTargetTitle(Report report) {
        if (report.getReviewId() != null) {
            return "Review #" + report.getReviewId();
        } else if (report.getPoiId() != null) {
            return "POI #" + report.getPoiId();
        }
        return "Unknown target";
    }

    private void sendNewReportNotification(Report report) {
        // Отправляем уведомление модераторам о новой жалобе
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("reportId", report.getId());
        notificationData.put("reportType", report.getReportType());
        notificationData.put("targetType", report.getReviewId() != null ? "review" : "poi");
        notificationData.put("targetId", report.getReviewId() != null ? report.getReviewId() : report.getPoiId());

        notificationClient.sendModeratorNotification("new_report", notificationData);

        log.info("New report notification sent for report: {}", report.getId());
    }

    private void sendReportResolutionNotification(Report report, String moderatorComment) {
        // Отправляем уведомление пользователю о результате модерации
        Map<String, Object> userNotificationData = new HashMap<>();
        userNotificationData.put("reportId", report.getId());
        userNotificationData.put("status", report.getStatus());
        userNotificationData.put("comment", moderatorComment);
        userNotificationData.put("handledAt", report.getHandledAt());

        notificationClient.sendUserNotification(report.getUserId(), "report_resolution", userNotificationData);

        log.info("Report resolution notification sent to user: {} for report: {}",
                report.getUserId(), report.getId());
    }

    private void handleApprovedReport(Report report) {
        // Если жалоба на отзыв - скрываем отзыв
        if (report.getReviewId() != null) {
            // Здесь должен быть вызов ReviewService для скрытия отзыва
            // reviewService.hideReview(report.getReviewId(), report.getHandledByUserId());
            log.info("Report approved: hiding review {}", report.getReviewId());
        }
        // Если жалоба на POI - отправляем уведомление администратору POI сервиса
        else if (report.getPoiId() != null) {
            Map<String, Object> poiReportData = new HashMap<>();
            poiReportData.put("reportId", report.getId());
            poiReportData.put("reportType", report.getReportType());
            poiReportData.put("comment", report.getComment());

            poiClient.notifyPoiIssue(report.getPoiId(), poiReportData);
            log.info("Report approved: notifying about POI issue {}", report.getPoiId());
        }
    }
}