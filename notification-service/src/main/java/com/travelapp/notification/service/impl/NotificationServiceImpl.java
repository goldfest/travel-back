package com.travelapp.notification.service.impl;

import com.travelapp.notification.client.AuthClient;
import com.travelapp.notification.exception.NotificationNotFoundException;
import com.travelapp.notification.exception.ResourceNotFoundException;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private static final List<String> ROUTE_SCHEDULED_TYPES = List.of(
            Notification.Type.ROUTE_DAY_START.getValue(),
            Notification.Type.ROUTE_REMINDER.getValue()
    );

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final EmailService emailService;
    private final AuthClient authClient;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "notifications", key = "#request.userId"),
            @CacheEvict(value = "notificationStats", key = "#request.userId")
    })
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        log.info("Creating notification for user {} type={} eventKey={}", request.getUserId(), request.getType(), request.getEventKey());
        validateUserExists(request.getUserId());

        if (request.getEventKey() != null && !request.getEventKey().isBlank()) {
            Optional<Notification> existing = notificationRepository.findByEventKey(request.getEventKey());
            if (existing.isPresent()) {
                return mapToResponse(existing.get());
            }
        }

        Notification notification = notificationMapper.toEntity(request);
        Notification saved = notificationRepository.save(notification);

        if (saved.getScheduledAt() == null) {
            saved.markAsSent();
            saved = notificationRepository.save(saved);
            if (shouldSendEmail(saved)) {
                sendEmailAsync(saved);
            }
        }

        return mapToResponse(saved);
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
            notifications = notificationRepository.findByUserIdAndTypeAndIsRead(userId, filter.getType(), filter.getIsRead(), pageable);
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
                .orElseThrow(() -> new NotificationNotFoundException(String.format("Notification with id %d not found", id)));

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
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unread);
    }

    @Override
    public void deleteNotification(Long id, Long userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(String.format("Notification with id %d not found", id)));

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
    public void deleteScheduledRouteNotifications(Long routeId) {
        notificationRepository.deleteByRouteIdAndTypes(routeId, ROUTE_SCHEDULED_TYPES);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationStatsResponse getUserNotificationStats(Long userId) {
        Long totalCount = notificationRepository.countByUserId(userId);
        Long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);

        List<Object[]> typeCounts = notificationRepository.countByUserIdGroupByType(userId);
        Map<String, Long> typeCountMap = new HashMap<>();
        typeCounts.forEach(result -> typeCountMap.put((String) result[0], (Long) result[1]));

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
        List<Notification> scheduled = notificationRepository.findScheduledReady(now);
        if (scheduled.isEmpty()) {
            return List.of();
        }

        for (Notification n : scheduled) {
            try {
                n.markAsSent();
                if (shouldSendEmail(n)) {
                    sendEmailAsync(n);
                }
            } catch (Exception e) {
                n.markAsFailed();
                log.error("Failed to activate scheduled notification {}: {}", n.getId(), e.getMessage(), e);
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
        for (CreateNotificationRequest request : requests) {
            createNotification(request);
        }
    }

    private void validateUserExists(Long userId) {
        if (!Boolean.TRUE.equals(authClient.exists(userId))) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
    }

    private void sendEmailAsync(Notification notification) {
        try {
            emailService.sendNotificationEmail(NotificationResponse.fromEntity(notification));
        } catch (Exception e) {
            log.warn("Failed to send email for notification {}: {}", notification.getId(), e.getMessage());
        }
    }

    private boolean shouldSendEmail(Notification notification) {
        if (notification.getType() == null) {
            return false;
        }
        return Notification.Type.SYSTEM.getValue().equalsIgnoreCase(notification.getType())
                || Notification.Type.ROUTE_REMINDER.getValue().equalsIgnoreCase(notification.getType())
                || Notification.Type.POI_UPDATE.getValue().equalsIgnoreCase(notification.getType());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return notificationMapper.toResponse(notification);
    }
}
