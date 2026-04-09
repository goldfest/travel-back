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
    public ResponseEntity<NotificationResponse> createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/internal/notifications/routes/{routeId}/scheduled")
    public ResponseEntity<Void> deleteRouteScheduledNotifications(@PathVariable Long routeId) {
        notificationService.deleteScheduledRouteNotifications(routeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/notifications/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getNotificationById(id, userId));
    }

    @GetMapping("/v1/notifications")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }

    @GetMapping("/v1/notifications/filter")
    public ResponseEntity<Page<NotificationResponse>> getMyNotificationsWithFilter(@ModelAttribute NotificationFilterRequest filter) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotificationsWithFilter(userId, filter));
    }

    @PatchMapping("/v1/notifications/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.markAsRead(id, userId));
    }

    @PatchMapping("/v1/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        Long userId = SecurityUtils.requireUserId();
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/v1/notifications/stats")
    public ResponseEntity<NotificationStatsResponse> getMyNotificationStats() {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(notificationService.getUserNotificationStats(userId));
    }

    @DeleteMapping("/v1/notifications/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/notifications")
    public ResponseEntity<Void> deleteAllMyNotifications() {
        Long userId = SecurityUtils.requireUserId();
        notificationService.deleteAllUserNotifications(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/internal/notifications/batch")
    public ResponseEntity<Void> createBatchNotifications(@Valid @RequestBody List<CreateNotificationRequest> requests) {
        notificationService.sendBatchNotifications(requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/internal/notifications/admin/scheduled")
    public ResponseEntity<List<NotificationResponse>> sendScheduledNotifications() {
        return ResponseEntity.ok(notificationService.sendScheduledNotifications());
    }
}
