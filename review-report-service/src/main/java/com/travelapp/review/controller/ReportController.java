package com.travelapp.review.controller;

import com.travelapp.review.model.dto.request.CreateReportRequest;
import com.travelapp.review.model.dto.request.UpdateReportRequest;
import com.travelapp.review.model.dto.response.ReportResponse;
import com.travelapp.review.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "API для управления жалобами и модерацией")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Создать жалобу",
            description = "Создает новую жалобу на отзыв или POI")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Жалоба успешно создана"),
            @ApiResponse(responseCode = "400", description = "Неверные данные жалобы"),
            @ApiResponse(responseCode = "404", description = "Цель жалобы не найдена")
    })
    public ResponseEntity<ReportResponse> createReport(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateReportRequest request) {

        ReportResponse response = reportService.createReport(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить жалобу по ID",
            description = "Возвращает информацию о жалобе по ее идентификатору")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Жалоба найдена"),
            @ApiResponse(responseCode = "404", description = "Жалоба не найдена")
    })
    public ResponseEntity<ReportResponse> getReport(@PathVariable Long id) {
        ReportResponse response = reportService.getReportById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Получить все жалобы",
            description = "Возвращает список всех жалоб с пагинацией (для модераторов)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список жалоб получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    public ResponseEntity<Page<ReportResponse>> getAllReports(
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportResponse> reports = reportService.getAllReports(pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Получить жалобы по статусу",
            description = "Возвращает список жалоб с указанным статусом")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список жалоб получен"),
            @ApiResponse(responseCode = "400", description = "Неверный статус"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    public ResponseEntity<Page<ReportResponse>> getReportsByStatus(
            @PathVariable String status,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportResponse> reports = reportService.getReportsByStatus(status, pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Получить жалобы пользователя",
            description = "Возвращает список жалоб, созданных пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список жалоб получен"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен")
    })
    public ResponseEntity<Page<ReportResponse>> getReportsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportResponse> reports = reportService.getReportsByUserId(userId, pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/my")
    @Operation(summary = "Получить мои жалобы",
            description = "Возвращает список жалоб, созданных текущим пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список жалоб получен")
    })
    public ResponseEntity<Page<ReportResponse>> getMyReports(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportResponse> reports = reportService.getReportsByUserId(userId, pageable);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/moderator/{moderatorId}")
    @Operation(summary = "Получить жалобы модератора",
            description = "Возвращает список жалоб, обработанных модератором")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список жалоб получен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    public ResponseEntity<Page<ReportResponse>> getReportsByModeratorId(
            @PathVariable Long moderatorId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReportResponse> reports = reportService.getReportsByModeratorId(moderatorId, pageable);
        return ResponseEntity.ok(reports);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить жалобу",
            description = "Обновляет статус и информацию о жалобе (для модераторов)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Жалоба успешно обновлена"),
            @ApiResponse(responseCode = "400", description = "Неверные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Жалоба не найдена")
    })
    public ResponseEntity<ReportResponse> updateReport(
            @PathVariable Long id,
            @AuthenticationPrincipal Long moderatorId,
            @Valid @RequestBody UpdateReportRequest request) {

        ReportResponse response = reportService.updateReport(id, moderatorId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить жалобу",
            description = "Удаляет жалобу по идентификатору (для модераторов)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Жалоба успешно удалена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Жалоба не найдена")
    })
    public ResponseEntity<Void> deleteReport(
            @PathVariable Long id,
            @AuthenticationPrincipal Long moderatorId) {

        reportService.deleteReport(id, moderatorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pending/count")
    @Operation(summary = "Получить количество ожидающих жалоб",
            description = "Возвращает количество жалоб со статусом 'pending'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Количество получено"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    public ResponseEntity<Long> getPendingReportsCount() {
        Long count = reportService.getPendingReportsCount();
        return ResponseEntity.ok(count);
    }

    @PostMapping("/{id}/process")
    @Operation(summary = "Обработать жалобу",
            description = "Обрабатывает жалобу с указанным статусом и комментарием")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Жалоба обработана"),
            @ApiResponse(responseCode = "400", description = "Неверный статус"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Жалоба не найдена")
    })
    public ResponseEntity<Void> processReport(
            @PathVariable Long id,
            @AuthenticationPrincipal Long moderatorId,
            @RequestParam String status,
            @RequestParam(required = false) String comment) {

        reportService.processReport(id, moderatorId, status, comment);
        return ResponseEntity.ok().build();
    }
}