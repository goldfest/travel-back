// notification-service/src/main/java/com/travelapp/notification/service/impl/NotificationServiceImpl.java
package com.travelapp.notification.service.impl;

import com.travelapp.notification.exception.NotificationNotFoundException;
import com.travelapp.notification.exception.UnauthorizedAccessException;
import com.travelapp.notification.mapper.NotificationMapper;
import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.request.NotificationFilterRequest;
import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.dto.response.NotificationStatsResponse;
import com.travelapp.notification.model.entity.Notification;
import com.travelapp.notification.repository.NotificationRepository;
import com.travelapp.notification.service.EmailService;
import com.travelapp.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final EmailService emailService;

    @Override
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        log.info("Creating notification for user {} with type {}", request.getUserId(), request.getType());

        Notification notification = notificationMapper.toEntity(request);

        // Если запланированное время не указано, отправляем немедленно
        if (notification.getScheduledAt() == null) {
            notification.markAsSent();
        }

        Notification savedNotification = notificationRepository.save(notification);

        // Если уведомление должно быть отправлено немедленно, отправляем его
        if (savedNotification.getScheduledAt() == null) {
            sendNotificationImmediately(NotificationResponse.fromEntity(savedNotification));
        }

        log.info("Notification created successfully with id: {}", savedNotification.getId());
        return notificationMapper.toResponse(savedNotification);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id, Long userId) {
        log.info("Getting notification with id {} for user {}", id, userId);

        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found for user %d", id, userId)));

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        log.info("Getting notifications for user {} with pageable {}", userId, pageable);

        Page<Notification> notifications = notificationRepository.findByUserId(userId, pageable);
        List<NotificationResponse> responses = notificationMapper.toResponseList(notifications.getContent());

        return new PageImpl<>(responses, pageable, notifications.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotificationsWithFilter(Long userId, NotificationFilterRequest filter) {
        log.info("Getting filtered notifications for user {} with filter {}", userId, filter);

        Page<Notification> notifications;
        Pageable pageable = filter.toPageable();

        if (filter.getType() != null && filter.getIsRead() != null) {
            notifications = notificationRepository.findByUserIdAndTypeAndIsRead(
                    userId, filter.getType(), filter.getIsRead(), pageable);
        } else if (filter.getType() != null) {
            notifications = notificationRepository.findByUserIdAndType(userId, filter.getType(), pageable);
        } else if (filter.getIsRead() != null) {
            notifications = notificationRepository.findByUserIdAndIsRead(userId, filter.getIsRead(), pageable);
        } else {
            notifications = notificationRepository.findByUserId(userId, pageable);
        }

        List<NotificationResponse> responses = notificationMapper.toResponseList(notifications.getContent());

        return new PageImpl<>(responses, pageable, notifications.getTotalElements());
    }

    @Override
    public NotificationResponse markAsRead(Long id, Long userId) {
        log.info("Marking notification {} as read for user {}", id, userId);

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found", id)));

        // Проверяем, что уведомление принадлежит пользователю
        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to access this notification");
        }

        notification.markAsRead();
        Notification updatedNotification = notificationRepository.save(notification);

        log.info("Notification {} marked as read", id);
        return notificationMapper.toResponse(updatedNotification);
    }

    @Override
    public void markAllAsRead(Long userId) {
        log.info("Marking all notifications as read for user {}", userId);

        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndIsReadFalse(userId);

        if (!unreadNotifications.isEmpty()) {
            unreadNotifications.forEach(Notification::markAsRead);
            notificationRepository.saveAll(unreadNotifications);
            log.info("Marked {} notifications as read for user {}", unreadNotifications.size(), userId);
        }
    }

    @Override
    public void deleteNotification(Long id, Long userId) {
        log.info("Deleting notification {} for user {}", id, userId);

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found", id)));

        // Проверяем, что уведомление принадлежит пользователю
        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to delete this notification");
        }

        notificationRepository.delete(notification);
        log.info("Notification {} deleted successfully", id);
    }

    @Override
    public void deleteAllUserNotifications(Long userId) {
        log.info("Deleting all notifications for user {}", userId);

        notificationRepository.deleteByUserId(userId);
        log.info("All notifications deleted for user {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationStatsResponse getUserNotificationStats(Long userId) {
        log.info("Getting notification stats for user {}", userId);

        Long totalCount = notificationRepository.countByUserId(userId);
        Long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);

        List<Object[]> typeCounts = notificationRepository.countByUserIdGroupByType(userId);

        Map<String, Long> typeCountMap = new HashMap<>();
        typeCounts.forEach(result -> {
            String type = (String) result[0];
            Long count = (Long) result[1];
            typeCountMap.put(type, count);
        });

        return NotificationStatsResponse.builder()
                .totalCount(totalCount)
                .unreadCount(unreadCount)
                .routeRemindersCount(typeCountMap.getOrDefault(Notification.Type.ROUTE_REMINDER.getValue(), 0L))
                .reviewNotificationsCount(typeCountMap.getOrDefault(Notification.Type.REVIEW.getValue(), 0L))
                .moderationNotificationsCount(typeCountMap.getOrDefault(Notification.Type.MODERATION.getValue(), 0L))
                .poiUpdateNotificationsCount(typeCountMap.getOrDefault(Notification.Type.POI_UPDATE.getValue(), 0L))
                .build();
    }

    @Override
    @Scheduled(fixedDelayString = "${notification.scheduler.fixed-delay:60000}")
    public List<NotificationResponse> sendScheduledNotifications() {
        log.info("Checking for scheduled notifications to send");

        LocalDateTime now = LocalDateTime.now();
        List<Notification> scheduledNotifications = notificationRepository
                .findByIsReadFalseAndScheduledAtBefore(now);

        if (scheduledNotifications.isEmpty()) {
            log.debug("No scheduled notifications to send");
            return List.of();
        }

        log.info("Found {} scheduled notifications to send", scheduledNotifications.size());

        scheduledNotifications.forEach(notification -> {
            notification.markAsSent();
            sendNotificationImmediately(NotificationResponse.fromEntity(notification));
        });

        notificationRepository.saveAll(scheduledNotifications);

        List<NotificationResponse> responses = notificationMapper.toResponseList(scheduledNotifications);
        log.info("Sent {} scheduled notifications", scheduledNotifications.size());

        return responses;
    }

    @Override
    public void sendNotificationImmediately(NotificationResponse notification) {
        log.info("Sending notification immediately: {}", notification.getTitle());

        // Здесь будет логика отправки уведомления через email, push, etc.
        try {
            emailService.sendNotificationEmail(notification);
            log.debug("Email notification sent for: {}", notification.getId());
        } catch (Exception e) {
            log.error("Failed to send email notification for {}: {}", notification.getId(), e.getMessage());
            // Можно добавить логику повторной попытки или отправки через другой канал
        }
    }

    @Override
    public void sendBatchNotifications(List<CreateNotificationRequest> requests) {
        log.info("Sending batch of {} notifications", requests.size());

        List<Notification> notifications = requests.stream()
                .map(notificationMapper::toEntity)
                .peek(notification -> notification.markAsSent())
                .collect(Collectors.toList());

        List<Notification> savedNotifications = notificationRepository.saveAll(notifications);

        savedNotifications.forEach(notification -> {
            try {
                sendNotificationImmediately(NotificationResponse.fromEntity(notification));
            } catch (Exception e) {
                log.error("Failed to send batch notification {}: {}", notification.getId(), e.getMessage());
            }
        });

        log.info("Batch notifications sent successfully");
    }
}