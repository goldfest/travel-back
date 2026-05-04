package com.travelapp.review.service;

import com.travelapp.review.client.PoiClient;
import com.travelapp.review.exception.ResourceNotFoundException;
import com.travelapp.review.mapper.ReportMapper;
import com.travelapp.review.model.dto.InternalUserResponse;
import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.model.entity.Report;
import com.travelapp.review.repository.ReportRepository;
import com.travelapp.review.repository.ReviewRepository;
import com.travelapp.review.service.impl.ReportServiceImpl;
import com.travelapp.review.service.media.ReviewMediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private PoiClient poiClient;

    @Mock
    private AuthUserService authUserService;

    @Mock
    private ReviewMediaStorageService mediaStorageService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @Test
    void createReport_shouldCreatePendingReportForPoi_whenPoiExists() {
        Long userId = 10L;
        CreateReportRequest request = createPoiReportRequest(100L);
        Report report = reportEntity(null, userId, 100L, null, null);
        Report savedReport = reportEntity(1L, userId, 100L, null, "pending");
        ReportResponse response = reportResponse(1L, userId, 100L, null, "pending");

        when(poiClient.checkPoiExists(100L)).thenReturn(true);
        doNothing().when(poiClient).reportPoi(100L, null);
        when(reportMapper.toEntity(request)).thenReturn(report);
        when(reportRepository.save(report)).thenReturn(savedReport);
        when(reportMapper.toResponse(savedReport)).thenReturn(response);
        when(authUserService.getUserInfo(userId)).thenReturn(userResponse(userId, "stepan"));

        ReportResponse result = reportService.createReport(userId, request);

        assertThat(result).isEqualTo(response);
        assertThat(report.getUserId()).isEqualTo(userId);
        assertThat(report.getStatus()).isEqualTo("pending");
        assertThat(response.getUserName()).isEqualTo("stepan");
        assertThat(response.getUserAvatarUrl()).isEqualTo("https://example.com/avatar.png");

        verify(poiClient).checkPoiExists(100L);
        verify(poiClient).reportPoi(100L, null);
        verify(reportRepository).save(report);
    }

    @Test
    void createReport_shouldThrowResourceNotFoundException_whenPoiDoesNotExist() {
        Long userId = 10L;
        CreateReportRequest request = createPoiReportRequest(404L);

        when(poiClient.checkPoiExists(404L)).thenReturn(false);

        assertThatThrownBy(() -> reportService.createReport(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("POI not found with id: 404");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_shouldCreatePendingReportForReview_whenReviewExists() {
        Long userId = 10L;
        CreateReportRequest request = createReviewReportRequest(5L);
        Report report = reportEntity(null, userId, null, 5L, null);
        Report savedReport = reportEntity(1L, userId, null, 5L, "pending");
        ReportResponse response = reportResponse(1L, userId, null, 5L, "pending");

        when(reviewRepository.existsById(5L)).thenReturn(true);
        when(reportMapper.toEntity(request)).thenReturn(report);
        when(reportRepository.save(report)).thenReturn(savedReport);
        when(reportMapper.toResponse(savedReport)).thenReturn(response);
        when(authUserService.getUserInfo(userId)).thenReturn(userResponse(userId, "stepan"));

        ReportResponse result = reportService.createReport(userId, request);

        assertThat(result).isEqualTo(response);
        assertThat(report.getUserId()).isEqualTo(userId);
        assertThat(report.getStatus()).isEqualTo("pending");
        verify(reviewRepository).existsById(5L);
        verify(poiClient, never()).checkPoiExists(any());
    }

    @Test
    void createReport_shouldThrowResourceNotFoundException_whenReviewDoesNotExist() {
        Long userId = 10L;
        CreateReportRequest request = createReviewReportRequest(404L);

        when(reviewRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> reportService.createReport(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Review not found with id: 404");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_shouldThrowIllegalArgumentException_whenBothPoiIdAndReviewIdAreProvided() {
        Long userId = 10L;
        CreateReportRequest request = CreateReportRequest.builder()
                .reportType("incorrect_info")
                .comment("Некорректные данные")
                .poiId(100L)
                .reviewId(5L)
                .build();

        assertThatThrownBy(() -> reportService.createReport(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Report must target either reviewId or poiId");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void createReport_shouldThrowIllegalArgumentException_whenNeitherPoiIdNorReviewIdAreProvided() {
        Long userId = 10L;
        CreateReportRequest request = CreateReportRequest.builder()
                .reportType("incorrect_info")
                .comment("Некорректные данные")
                .build();

        assertThatThrownBy(() -> reportService.createReport(userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Report must target either reviewId or poiId");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateReport_shouldUpdateCommentAndPhotoUrl_whenUserIsOwnerAndReportIsPending() {
        Long reportId = 1L;
        Long userId = 10L;
        Report report = reportEntity(reportId, userId, 100L, null, "pending");
        UpdateReportRequest request = new UpdateReportRequest();
        request.setComment("Обновлённое описание проблемы");
        request.setPhotoUrl("https://example.com/new-photo.jpg");
        ReportResponse response = reportResponse(reportId, userId, 100L, null, "pending");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);
        when(reportMapper.toResponse(report)).thenReturn(response);
        when(authUserService.getUserInfo(userId)).thenReturn(userResponse(userId, "stepan"));

        ReportResponse result = reportService.updateReport(reportId, userId, request);

        assertThat(result).isEqualTo(response);
        assertThat(report.getComment()).isEqualTo("Обновлённое описание проблемы");
        assertThat(report.getPhotoUrl()).isEqualTo("https://example.com/new-photo.jpg");
        verify(reportRepository).save(report);
    }

    @Test
    void updateReport_shouldThrowSecurityException_whenUserIsNotOwner() {
        Long reportId = 1L;
        Report report = reportEntity(reportId, 10L, 100L, null, "pending");
        UpdateReportRequest request = new UpdateReportRequest();
        request.setComment("Попытка изменения");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateReport(reportId, 20L, request))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("User is not authorized to update this report");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateReport_shouldThrowIllegalArgumentException_whenReportIsAlreadyProcessed() {
        Long reportId = 1L;
        Long userId = 10L;
        Report report = reportEntity(reportId, userId, 100L, null, "approved");
        UpdateReportRequest request = new UpdateReportRequest();
        request.setComment("Попытка изменения");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.updateReport(reportId, userId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only pending report can be updated");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void deleteReport_shouldDeleteReport_whenUserIsOwnerAndReportIsPending() {
        Long reportId = 1L;
        Long userId = 10L;
        Report report = reportEntity(reportId, userId, 100L, null, "pending");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        reportService.deleteReport(reportId, userId);

        verify(reportRepository).delete(report);
    }

    @Test
    void deleteReport_shouldThrowIllegalArgumentException_whenReportIsAlreadyProcessed() {
        Long reportId = 1L;
        Long userId = 10L;
        Report report = reportEntity(reportId, userId, 100L, null, "rejected");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.deleteReport(reportId, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only pending report can be deleted");

        verify(reportRepository, never()).delete(any());
    }

    @Test
    void processReport_shouldNormalizeStatusAndSetModeratorData() {
        Long reportId = 1L;
        Long moderatorId = 99L;
        Report report = reportEntity(reportId, 10L, 100L, null, "pending");
        ReportResponse response = reportResponse(reportId, 10L, 100L, null, "approved");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(reportRepository.save(report)).thenReturn(report);
        when(reportMapper.toResponse(report)).thenReturn(response);
        when(authUserService.getUserInfo(10L)).thenReturn(userResponse(10L, "stepan"));
        when(authUserService.getUserInfo(moderatorId)).thenReturn(userResponse(moderatorId, "moderator"));

        ReportResponse result = reportService.processReport(reportId, moderatorId, " APPROVED ", "Исправлено");

        assertThat(result).isEqualTo(response);
        assertThat(report.getStatus()).isEqualTo("approved");
        assertThat(report.getHandledByUserId()).isEqualTo(moderatorId);
        assertThat(report.getHandledAt()).isNotNull();
        assertThat(report.getModeratorComment()).isEqualTo("Исправлено");
        assertThat(response.getUserName()).isEqualTo("stepan");
        assertThat(response.getHandledByUserName()).isEqualTo("moderator");
        verify(reportRepository).save(report);
    }

    @Test
    void processReport_shouldThrowIllegalArgumentException_whenStatusIsInvalid() {
        Long reportId = 1L;
        Report report = reportEntity(reportId, 10L, 100L, null, "pending");

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.processReport(reportId, 99L, "pending", "Комментарий"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Allowed statuses: approved, rejected");

        verify(reportRepository, never()).save(any());
    }

    @Test
    void getPendingReportsCount_shouldReturnCountFromRepository() {
        when(reportRepository.countByStatus("pending")).thenReturn(7L);

        Long result = reportService.getPendingReportsCount();

        assertThat(result).isEqualTo(7L);
        verify(reportRepository).countByStatus("pending");
    }

    private CreateReportRequest createPoiReportRequest(Long poiId) {
        return CreateReportRequest.builder()
                .reportType("incorrect_info")
                .comment("Некорректная информация об объекте")
                .poiId(poiId)
                .build();
    }

    private CreateReportRequest createReviewReportRequest(Long reviewId) {
        return CreateReportRequest.builder()
                .reportType("offensive_review")
                .comment("Некорректный отзыв")
                .reviewId(reviewId)
                .build();
    }

    private Report reportEntity(Long id, Long userId, Long poiId, Long reviewId, String status) {
        return Report.builder()
                .id(id)
                .reportType("incorrect_info")
                .comment("Описание проблемы")
                .status(status)
                .photoUrl("https://example.com/photo.jpg")
                .userId(userId)
                .poiId(poiId)
                .reviewId(reviewId)
                .build();
    }

    private ReportResponse reportResponse(Long id, Long userId, Long poiId, Long reviewId, String status) {
        return ReportResponse.builder()
                .id(id)
                .reportType("incorrect_info")
                .comment("Описание проблемы")
                .status(status)
                .photoUrl("https://example.com/photo.jpg")
                .userId(userId)
                .poiId(poiId)
                .reviewId(reviewId)
                .build();
    }

    private InternalUserResponse userResponse(Long id, String username) {
        InternalUserResponse user = new InternalUserResponse();
        user.setId(id);
        user.setUsername(username);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setStatus("ACTIVE");
        user.setRole("USER");
        user.setIsBlocked(false);
        return user;
    }
}
