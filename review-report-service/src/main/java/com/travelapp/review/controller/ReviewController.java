package com.travelapp.review.controller;

import com.travelapp.review.model.dto.request.CreateReviewRequest;
import com.travelapp.review.model.dto.request.UpdateReviewRequest;
import com.travelapp.review.model.dto.response.PoiReviewStatsResponse;
import com.travelapp.review.model.dto.response.ReviewResponse;
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
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "API для управления отзывами")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "Создать новый отзыв",
            description = "Создает новый отзыв для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Отзыв успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неверные данные отзыва"),
            @ApiResponse(responseCode = "404", description = "POI не найден"),
            @ApiResponse(responseCode = "409", description = "Пользователь уже оставил отзыв для этого POI")
    })
    public ResponseEntity<ReviewResponse> createReview(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateReviewRequest request) {

        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Получить отзыв по ID",
            description = "Возвращает информацию об отзыве по его идентификатору")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв найден"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> getReview(
            @PathVariable Long id,
            @AuthenticationPrincipal(required = false) Long currentUserId) {

        ReviewResponse response = reviewService.getReviewById(id, currentUserId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/poi/{poiId}")
    @Operation(summary = "Получить отзывы для POI",
            description = "Возвращает список отзывов для указанного POI с пагинацией")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список отзывов получен")
    })
    public ResponseEntity<Page<ReviewResponse>> getReviewsByPoiId(
            @PathVariable Long poiId,
            @AuthenticationPrincipal(required = false) Long currentUserId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReviewResponse> reviews = reviewService.getReviewsByPoiId(poiId, currentUserId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Получить отзывы пользователя",
            description = "Возвращает список отзывов, оставленных пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список отзывов получен"),
            @ApiResponse(responseCode = "403", description = "Доступ запрещен")
    })
    public ResponseEntity<Page<ReviewResponse>> getReviewsByUserId(
            @PathVariable Long userId,
            @Parameter(description = "Параметры пагинации")
            @PageableDefault(size = 20) Pageable pageable) {

        Page<ReviewResponse> reviews = reviewService.getReviewsByUserId(userId, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/poi/{poiId}/user/{userId}")
    @Operation(summary = "Получить отзыв пользователя для POI",
            description = "Возвращает отзыв конкретного пользователя для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв найден"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> getReviewByPoiAndUser(
            @PathVariable Long poiId,
            @PathVariable Long userId) {

        ReviewResponse response = reviewService.getReviewByPoiAndUser(poiId, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить отзыв",
            description = "Обновляет информацию об отзыве")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв успешно обновлен"),
            @ApiResponse(responseCode = "400", description = "Неверные данные отзыва"),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет прав на обновление"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> updateReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateReviewRequest request) {

        ReviewResponse response = reviewService.updateReview(id, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить отзыв",
            description = "Удаляет отзыв по идентификатору")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Отзыв успешно удален"),
            @ApiResponse(responseCode = "403", description = "Пользователь не имеет прав на удаление"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        reviewService.deleteReview(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Поставить/убрать лайк отзыву",
            description = "Добавляет или удаляет лайк отзыва пользователем")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Лайк успешно обработан"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<ReviewResponse> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {

        ReviewResponse response = reviewService.toggleLike(id, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/poi/{poiId}/stats")
    @Operation(summary = "Получить статистику отзывов POI",
            description = "Возвращает статистику отзывов для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статистика получена")
    })
    public ResponseEntity<PoiReviewStatsResponse> getPoiReviewStats(
            @PathVariable Long poiId) {

        PoiReviewStatsResponse stats = reviewService.getPoiReviewStats(poiId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/check/{poiId}")
    @Operation(summary = "Проверить, оставил ли пользователь отзыв",
            description = "Проверяет, оставлял ли текущий пользователь отзыв для указанного POI")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Проверка выполнена")
    })
    public ResponseEntity<Boolean> hasUserReviewedPoi(
            @PathVariable Long poiId,
            @AuthenticationPrincipal Long userId) {

        boolean hasReviewed = reviewService.hasUserReviewedPoi(userId, poiId);
        return ResponseEntity.ok(hasReviewed);
    }

    @PostMapping("/{id}/hide")
    @Operation(summary = "Скрыть отзыв (модерация)",
            description = "Скрывает отзыв от публичного просмотра (для модераторов)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв скрыт"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<Void> hideReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Long moderatorId) {

        reviewService.hideReview(id, moderatorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unhide")
    @Operation(summary = "Показать отзыв (модерация)",
            description = "Восстанавливает отзыв для публичного просмотра (для модераторов)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Отзыв восстановлен"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Отзыв не найден")
    })
    public ResponseEntity<Void> unhideReview(
            @PathVariable Long id,
            @AuthenticationPrincipal Long moderatorId) {

        reviewService.unhideReview(id, moderatorId);
        return ResponseEntity.ok().build();
    }
}