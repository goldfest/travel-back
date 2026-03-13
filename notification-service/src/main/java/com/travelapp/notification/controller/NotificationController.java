package com.travelapp.notification.controller;

import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.request.NotificationFilterRequest;
import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.dto.response.NotificationStatsResponse;
import com.travelapp.notification.security.SecurityUtils;
import com.travelapp.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Уведомления", description = "API для управления уведомлениями пользователей")
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/internal/notifications")
    @Operation(summary = "Создать новое уведомление", description = "Создает новое уведомление для пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Уведомление успешно создано"),
            @ApiResponse(responseCode = "400", description = "Некорректные входные данные"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/v1/notifications/{id}")
    @Operation(summary = "Получить уведомление по ID", description = "Возвращает уведомление по указанному ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомление найдено"),
            @ApiResponse(responseCode = "404", description = "Уведомление не найдено"),
            @ApiResponse(responseCode = "403", description = "Нет доступа к уведомлению")
    })
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getNotificationById(id, userId));
    }

    @GetMapping("/v1/notifications")
    @Operation(summary = "Получить уведомления пользователя", description = "Возвращает список уведомлений пользователя с пагинацией")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомления найдены")
    })
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }

    @GetMapping("/v1/notifications/filter")
    @Operation(summary = "Получить уведомления пользователя с фильтрацией", description = "Возвращает отфильтрованный список уведомлений")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомления найдены")
    })
    public ResponseEntity<Page<NotificationResponse>> getMyNotificationsWithFilter(
            @ModelAttribute NotificationFilterRequest filter) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotificationsWithFilter(userId, filter));
    }

    @PatchMapping("/v1/notifications/{id}/read")
    @Operation(summary = "Отметить уведомление как прочитанное", description = "Отмечает уведомление как прочитанное")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Уведомление отмечено как прочитанное"),
            @ApiResponse(responseCode = "404", description = "Уведомление не найдено"),
            @ApiResponse(responseCode = "403", description = "Нет доступа к уведомлению")
    })
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.markAsRead(id, userId));
    }

    @PatchMapping("/v1/notifications/read-all")
    @Operation(summary = "Отметить все уведомления как прочитанные", description = "Отмечает все уведомления пользователя как прочитанные")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Все уведомления отмечены как прочитанные")
    })
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = SecurityUtils.requireUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/v1/notifications/stats")
    @Operation(summary = "Получить статистику уведомлений", description = "Возвращает статистику уведомлений пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статистика получена")
    })
    public ResponseEntity<NotificationStatsResponse> getMyNotificationStats() {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotificationStats(userId));
    }

    @DeleteMapping("/v1/notifications/{id}")
    @Operation(summary = "Удалить уведомление", description = "Удаляет уведомление по ID")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/notifications")
    @Operation(summary = "Удалить все уведомления пользователя", description = "Удаляет все уведомления текущего пользователя")
    public ResponseEntity<Void> deleteAllMyNotifications() {
        Long userId = SecurityUtils.requireUserId();
        notificationService.deleteAllUserNotifications(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/notifications/batch")
    @Operation(summary = "Создать несколько уведомлений", description = "Создает несколько уведомлений одновременно")
    public ResponseEntity<Void> createBatchNotifications(
            @Valid @RequestBody List<CreateNotificationRequest> requests) {
        notificationService.sendBatchNotifications(requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/internal/notifications/admin/scheduled")
    @Operation(summary = "Отправить запланированные уведомления", description = "Отправляет все запланированные уведомления, время которых наступило")
    public ResponseEntity<List<NotificationResponse>> sendScheduledNotifications() {
        return ResponseEntity.ok(notificationService.sendScheduledNotifications());
    }
}