// notification-service/src/main/java/com/travelapp/notification/service/NotificationService.java
package com.travelapp.notification.service;

import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.request.NotificationFilterRequest;
import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.dto.response.NotificationStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    NotificationResponse createNotification(CreateNotificationRequest request);

    NotificationResponse getNotificationById(Long id, Long userId);

    Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable);

    Page<NotificationResponse> getUserNotificationsWithFilter(Long userId, NotificationFilterRequest filter);

    NotificationResponse markAsRead(Long id, Long userId);

    void markAllAsRead(Long userId);

    void deleteNotification(Long id, Long userId);

    void deleteAllUserNotifications(Long userId);

    NotificationStatsResponse getUserNotificationStats(Long userId);

    List<NotificationResponse> sendScheduledNotifications();

    void sendNotificationImmediately(NotificationResponse notification);

    void sendBatchNotifications(List<CreateNotificationRequest> requests);
}