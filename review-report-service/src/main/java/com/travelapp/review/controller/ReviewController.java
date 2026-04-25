package com.travelapp.review.controller;

import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
import com.travelapp.review.security.SecurityUtils;
import com.travelapp.review.service.ReviewService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "API для управления отзывами")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "Создать новый отзыв", description = "Создает новый отзыв для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отзыв успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неверные данные отзыва"),
            @ApiResponse(responseCode = "404", description = "POI не найден"),
            @ApiResponse(responseCode = "409", description = "Пользователь уже оставил отзыв для этого POI")
    })
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();
        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/with-media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Создать отзыв с фотографиями", description = "Создает отзыв и прикрепляет фотографии. Отзыв с фото получает статус PENDING и отправляется на модерацию.")
    public ResponseEntity<ReviewResponse> createReviewWithMedia(
            @RequestParam Long poiId,
            @RequestParam Short rating,
            @RequestParam(required = false) String comment,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        Long userId = SecurityUtils.requireUserId();
        ReviewResponse response = reviewService.createReviewWithMedia(userId, poiId, rating, comment, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/moderation/pending")
    @Operation(summary = "Получить отзывы на модерации", description = "Возвращает отзывы, ожидающие проверки модератором")
    public ResponseEntity<Page<ReviewResponse>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.getPendingReviews(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить отзыв по ID", description = "Возвращает информацию об отзыве по его идентификатору")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв найден"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> getReview(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.requireUserId();
        ReviewResponse response = reviewService.getReviewById(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/poi/{poiId}")
    @Operation(summary = "Получить отзывы для POI", description = "Возвращает список одобренных отзывов для указанного POI с пагинацией")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Список отзывов получен"))
    public ResponseEntity<Page<ReviewResponse>> getReviewsByPoiId(
            @PathVariable Long poiId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Long userId = SecurityUtils.requireUserId();
        Page<ReviewResponse> reviews = reviewService.getReviewsByPoiId(poiId, userId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Получить отзывы пользователя", description = "Возвращает список одобренных отзывов, оставленных пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список отзывов получен"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен")
    })
    public ResponseEntity<Page<ReviewResponse>> getReviewsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ReviewResponse> reviews = reviewService.getReviewsByUserId(userId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/poi/{poiId}/user/{userId}")
    @Operation(summary = "Получить отзыв пользователя для POI", description = "Возвращает отзыв конкретного пользователя для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв найден"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> getReviewByPoiAndUser(
            @PathVariable Long poiId,
            @PathVariable Long userId
    ) {
        ReviewResponse response = reviewService.getReviewByPoiAndUser(poiId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить отзыв", description = "Обновляет информацию об отзыве")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв успешно обновлен"),
            @ApiResponse(responseCode = "400", description = "Неверные данные отзыва"),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет прав на обновление"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();
        ReviewResponse response = reviewService.updateReview(id, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Добавить фотографии к отзыву", description = "Добавляет фотографии к существующему отзыву и отправляет отзыв на повторную модерацию")
    public ResponseEntity<ReviewResponse> addMediaToReview(
            @PathVariable Long id,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(reviewService.addMediaToReview(id, userId, files));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить отзыв", description = "Удаляет отзыв по идентификатору")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв успешно удален"),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет прав на удаление"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.requireUserId();
        reviewService.deleteReview(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Поставить/убрать лайк отзыву", description = "Добавляет или удаляет лайк отзыва пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Лайк успешно обработан"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> toggleLike(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.requireUserId();
        ReviewResponse response = reviewService.toggleLike(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/poi/{poiId}/stats")
    @Operation(summary = "Получить статистику отзывов POI", description = "Возвращает статистику отзывов для указанного POI")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Статистика получена"))
    public ResponseEntity<PoiReviewStatsResponse> getPoiReviewStats(@PathVariable Long poiId) {
        PoiReviewStatsResponse stats = reviewService.getPoiReviewStats(poiId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/check/{poiId}")
    @Operation(summary = "Проверить, оставил ли пользователь отзыв", description = "Проверяет, оставлял ли текущий пользователь отзыв для указанного POI")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Проверка выполнена"))
    public ResponseEntity<Boolean> hasUserReviewedPoi(
            @PathVariable Long poiId
    ) {
        Long userId = SecurityUtils.requireUserId();
        boolean hasReviewed = reviewService.hasUserReviewedPoi(userId, poiId);
        return ResponseEntity.ok(hasReviewed);
    }

    @PostMapping("/{id}/hide")
    @Operation(summary = "Скрыть отзыв (модерация)", description = "Скрывает отзыв от публичного просмотра")
    public ResponseEntity<Void> hideReview(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        reviewService.hideReview(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unhide")
    @Operation(summary = "Показать отзыв (модерация)", description = "Одобряет отзыв и восстанавливает его для публичного просмотра")
    public ResponseEntity<Void> unhideReview(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        reviewService.unhideReview(id, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Одобрить отзыв", description = "Одобряет отзыв с фотографиями после модерации")
    public ResponseEntity<ReviewResponse> approveReview(
            @PathVariable Long id,
            @RequestParam(required = false) String moderationComment
    ) {
        Long moderatorId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(reviewService.approveReview(id, moderatorId, moderationComment));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Отклонить отзыв", description = "Отклоняет отзыв с фотографиями после модерации")
    public ResponseEntity<ReviewResponse> rejectReview(
            @PathVariable Long id,
            @RequestParam(required = false) String moderationComment
    ) {
        Long moderatorId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(reviewService.rejectReview(id, moderatorId, moderationComment));
    }
}
