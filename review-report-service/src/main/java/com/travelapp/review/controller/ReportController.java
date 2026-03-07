package com.travelapp.review.controller;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.ProcessReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "API для управления жалобами")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Создать жалобу")
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal(expression = "id") Long userId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.createReport(userId, request));
    }

    @GetMapping("/my")
    @Operation(summary = "Получить мои жалобы")
    public ResponseEntity<Page<ReportResponse>> getMyReports(
            @AuthenticationPrincipal(expression = "id") Long userId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReportsByUserId(userId, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить свою жалобу")
    public ResponseEntity<ReportResponse> updateReport(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long userId,
            @Valid @RequestBody UpdateReportRequest request
    ) {
        return ResponseEntity.ok(reportService.updateReport(id, userId, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить свою жалобу")
    public ResponseEntity<Void> deleteReport(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long userId
    ) {
        reportService.deleteReport(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Получить все жалобы (модерация)")
    public ResponseEntity<Page<ReportResponse>> getAllReports(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reportService.getAllReports(pageable));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Получить жалобы по статусу (модерация)")
    public ResponseEntity<Page<ReportResponse>> getReportsByStatus(
            @PathVariable String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReportsByStatus(status, pageable));
    }

    @GetMapping("/moderator/{moderatorId}")
    @Operation(summary = "Получить жалобы модератора")
    public ResponseEntity<Page<ReportResponse>> getReportsByModeratorId(
            @PathVariable Long moderatorId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reportService.getReportsByModeratorId(moderatorId, pageable));
    }

    @GetMapping("/stats/pending-count")
    @Operation(summary = "Количество pending-жалоб")
    public ResponseEntity<Long> getPendingReportsCount() {
        return ResponseEntity.ok(reportService.getPendingReportsCount());
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Обработать жалобу")
    public ResponseEntity<ReportResponse> processReport(
            @PathVariable Long id,
            @AuthenticationPrincipal(expression = "id") Long moderatorId,
            @Valid @RequestBody ProcessReportRequest request
    ) {
        return ResponseEntity.ok(
                reportService.processReport(id, moderatorId, request.getStatus(), request.getModeratorComment())
        );
    }
}