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

        // scheduledAt == null -> отправка сразу, но sentAt ставим ТОЛЬКО после успеха отправки
        Notification saved = notificationRepository.save(notification);

        if (saved.getScheduledAt() == null) {
            sendNotificationImmediately(NotificationResponse.fromEntity(saved));
            // если отправка успешна -> ставим sentAt и сохраняем
            saved.markAsSent();
            saved = notificationRepository.save(saved);
        }

        log.info("Notification created successfully with id: {}", saved.getId());
        return notificationMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationResponse getNotificationById(Long id, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found for user %d", id, userId)));
        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Pageable pageable) {
        Page<Notification> notifications = notificationRepository.findByUserId(userId, pageable);
        List<NotificationResponse> responses = notificationMapper.toResponseList(notifications.getContent());
        return new PageImpl<>(responses, pageable, notifications.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotificationsWithFilter(Long userId, NotificationFilterRequest filter) {
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
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found", id)));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to access this notification");
        }

        notification.markAsRead();
        Notification updated = notificationRepository.save(notification);
        return notificationMapper.toResponse(updated);
    }

    @Override
    public void markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        if (unread.isEmpty()) return;

        unread.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unread);
        log.info("Marked {} notifications as read for user {}", unread.size(), userId);
    }

    @Override
    public void deleteNotification(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(
                        String.format("Notification with id %d not found", id)));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedAccessException("User is not authorized to delete this notification");
        }

        notificationRepository.delete(notification);
    }

    @Override
    public void deleteAllUserNotifications(Long userId) {
        notificationRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationStatsResponse getUserNotificationStats(Long userId) {
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
        LocalDateTime now = LocalDateTime.now();

        // ✅ правильная выборка: scheduledAt <= now и ещё НЕ отправляли
        List<Notification> scheduled = notificationRepository.findScheduledReady(now);

        if (scheduled.isEmpty()) {
            log.debug("No scheduled notifications to send");
            return List.of();
        }

        log.info("Found {} scheduled notifications to send", scheduled.size());

        for (Notification n : scheduled) {
            try {
                sendNotificationImmediately(NotificationResponse.fromEntity(n));
                n.markAsSent(); // ✅ только после успешной отправки
            } catch (Exception e) {
                // sentAt не ставим — останется в очереди на следующую попытку
                log.error("Failed to send scheduled notification {}: {}", n.getId(), e.getMessage(), e);
            }
        }

        notificationRepository.saveAll(scheduled);
        return notificationMapper.toResponseList(scheduled);
    }

    @Override
    public void sendNotificationImmediately(NotificationResponse notification) {
        emailService.sendNotificationEmail(notification);
    }

    @Override
    public void sendBatchNotifications(List<CreateNotificationRequest> requests) {
        log.info("Sending batch of {} notifications", requests.size());

        // ✅ для batch: тоже ставим sentAt после успеха отправки
        List<Notification> saved = notificationRepository.saveAll(
                requests.stream().map(notificationMapper::toEntity).collect(Collectors.toList())
        );

        for (Notification n : saved) {
            try {
                sendNotificationImmediately(NotificationResponse.fromEntity(n));
                n.markAsSent();
            } catch (Exception e) {
                log.error("Failed to send batch notification {}: {}", n.getId(), e.getMessage(), e);
            }
        }

        notificationRepository.saveAll(saved);
    }
}