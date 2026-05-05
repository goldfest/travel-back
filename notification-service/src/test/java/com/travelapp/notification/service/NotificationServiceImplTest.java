package com.travelapp.notification.service;

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
import com.travelapp.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private EmailService emailService;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void createNotification_shouldSaveMarkAsSentAndSendEmail_whenNotificationIsImmediateSystemType() {
        CreateNotificationRequest request = validRequest(Notification.Type.SYSTEM.getValue());
        Notification notification = notification(null, request.getUserId(), request.getType());
        NotificationResponse response = response(1L, request.getUserId(), request.getType());

        when(authClient.exists(request.getUserId())).thenReturn(true);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
        when(notificationMapper.toResponse(any(Notification.class))).thenReturn(response);

        NotificationResponse result = notificationService.createNotification(request);

        assertThat(result).isEqualTo(response);
        assertThat(notification.getStatus()).isEqualTo("SENT");
        assertThat(notification.getSentAt()).isNotNull();
        verify(authClient).exists(request.getUserId());
        verify(notificationRepository, times(2)).save(notification);
        verify(emailService).sendNotificationEmail(any(NotificationResponse.class));
    }

    @Test
    void createNotification_shouldNotSendEmail_whenTypeDoesNotRequireEmail() {
        CreateNotificationRequest request = validRequest(Notification.Type.REVIEW.getValue());
        Notification notification = notification(null, request.getUserId(), request.getType());
        NotificationResponse response = response(1L, request.getUserId(), request.getType());

        when(authClient.exists(request.getUserId())).thenReturn(true);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });
        when(notificationMapper.toResponse(any(Notification.class))).thenReturn(response);

        NotificationResponse result = notificationService.createNotification(request);

        assertThat(result).isEqualTo(response);
        assertThat(notification.getStatus()).isEqualTo("SENT");
        verify(emailService, never()).sendNotificationEmail(any());
    }

    @Test
    void createNotification_shouldSaveOnlyPendingNotification_whenScheduledAtIsPresent() {
        CreateNotificationRequest request = validRequest(Notification.Type.ROUTE_REMINDER.getValue());
        request.setScheduledAt(LocalDateTime.now().plusHours(2));
        Notification notification = notification(null, request.getUserId(), request.getType());
        notification.setScheduledAt(request.getScheduledAt());
        NotificationResponse response = response(1L, request.getUserId(), request.getType());

        when(authClient.exists(request.getUserId())).thenReturn(true);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenAnswer(invocation -> {
            notification.setId(1L);
            return notification;
        });
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        NotificationResponse result = notificationService.createNotification(request);

        assertThat(result).isEqualTo(response);
        assertThat(notification.getStatus()).isEqualTo("PENDING");
        assertThat(notification.getSentAt()).isNull();
        verify(notificationRepository, times(1)).save(notification);
        verify(emailService, never()).sendNotificationEmail(any());
    }

    @Test
    void createNotification_shouldReturnExistingNotification_whenEventKeyAlreadyExists() {
        CreateNotificationRequest request = validRequest(Notification.Type.SYSTEM.getValue());
        request.setEventKey("route-1-day-start");
        Notification existing = notification(5L, request.getUserId(), request.getType());
        existing.setEventKey(request.getEventKey());
        NotificationResponse response = response(5L, request.getUserId(), request.getType());

        when(authClient.exists(request.getUserId())).thenReturn(true);
        when(notificationRepository.findByEventKey(request.getEventKey())).thenReturn(Optional.of(existing));
        when(notificationMapper.toResponse(existing)).thenReturn(response);

        NotificationResponse result = notificationService.createNotification(request);

        assertThat(result).isEqualTo(response);
        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendNotificationEmail(any());
    }

    @Test
    void createNotification_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        CreateNotificationRequest request = validRequest(Notification.Type.SYSTEM.getValue());
        when(authClient.exists(request.getUserId())).thenReturn(false);

        assertThatThrownBy(() -> notificationService.createNotification(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendNotificationEmail(any());
    }

    @Test
    void getNotificationById_shouldReturnNotification_whenItBelongsToUser() {
        Long userId = 10L;
        Long notificationId = 1L;
        Notification notification = notification(notificationId, userId, Notification.Type.SYSTEM.getValue());
        NotificationResponse response = response(notificationId, userId, Notification.Type.SYSTEM.getValue());

        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        NotificationResponse result = notificationService.getNotificationById(notificationId, userId);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getNotificationById_shouldThrowNotificationNotFoundException_whenNotificationDoesNotBelongToUser() {
        Long userId = 10L;
        Long notificationId = 1L;
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getNotificationById(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getUserNotifications_shouldReturnMappedPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        Notification notification = notification(1L, userId, Notification.Type.SYSTEM.getValue());
        NotificationResponse response = response(1L, userId, Notification.Type.SYSTEM.getValue());
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);

        when(notificationRepository.findByUserId(userId, pageable)).thenReturn(page);
        when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

        Page<NotificationResponse> result = notificationService.getUserNotifications(userId, pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getUserNotificationsWithFilter_shouldUseTypeAndReadFilter_whenBothArePresent() {
        Long userId = 10L;
        NotificationFilterRequest filter = new NotificationFilterRequest(
                Notification.Type.ROUTE_REMINDER.getValue(),
                false,
                0,
                20,
                "createdAt",
                Sort.Direction.DESC
        );
        Pageable pageable = filter.toPageable();
        Notification notification = notification(1L, userId, Notification.Type.ROUTE_REMINDER.getValue());
        NotificationResponse response = response(1L, userId, Notification.Type.ROUTE_REMINDER.getValue());
        Page<Notification> page = new PageImpl<>(List.of(notification), pageable, 1);

        when(notificationRepository.findByUserIdAndTypeAndIsRead(userId, filter.getType(), filter.getIsRead(), pageable))
                .thenReturn(page);
        when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

        Page<NotificationResponse> result = notificationService.getUserNotificationsWithFilter(userId, filter);

        assertThat(result.getContent()).containsExactly(response);
        verify(notificationRepository).findByUserIdAndTypeAndIsRead(userId, filter.getType(), false, pageable);
        verify(notificationRepository, never()).findByUserIdAndType(eq(userId), any(), any());
        verify(notificationRepository, never()).findByUserIdAndIsRead(eq(userId), any(), any());
    }

    @Test
    void getUserNotificationsWithFilter_shouldUseTypeFilter_whenOnlyTypeIsPresent() {
        Long userId = 10L;
        NotificationFilterRequest filter = new NotificationFilterRequest(
                Notification.Type.MODERATION.getValue(), null, 0, 20, "createdAt", Sort.Direction.DESC
        );
        Pageable pageable = filter.toPageable();
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);

        when(notificationRepository.findByUserIdAndType(userId, filter.getType(), pageable)).thenReturn(page);
        when(notificationMapper.toResponseList(List.of())).thenReturn(List.of());

        Page<NotificationResponse> result = notificationService.getUserNotificationsWithFilter(userId, filter);

        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository).findByUserIdAndType(userId, filter.getType(), pageable);
    }

    @Test
    void getUserNotificationsWithFilter_shouldUseReadFilter_whenOnlyIsReadIsPresent() {
        Long userId = 10L;
        NotificationFilterRequest filter = new NotificationFilterRequest(null, true, 0, 20, "createdAt", Sort.Direction.DESC);
        Pageable pageable = filter.toPageable();
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);

        when(notificationRepository.findByUserIdAndIsRead(userId, true, pageable)).thenReturn(page);
        when(notificationMapper.toResponseList(List.of())).thenReturn(List.of());

        Page<NotificationResponse> result = notificationService.getUserNotificationsWithFilter(userId, filter);

        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository).findByUserIdAndIsRead(userId, true, pageable);
    }

    @Test
    void getUserNotificationsWithFilter_shouldUseDefaultUserFilter_whenNoFiltersArePresent() {
        Long userId = 10L;
        NotificationFilterRequest filter = new NotificationFilterRequest(null, null, 0, 20, "createdAt", Sort.Direction.DESC);
        Pageable pageable = filter.toPageable();
        Page<Notification> page = new PageImpl<>(List.of(), pageable, 0);

        when(notificationRepository.findByUserId(userId, pageable)).thenReturn(page);
        when(notificationMapper.toResponseList(List.of())).thenReturn(List.of());

        Page<NotificationResponse> result = notificationService.getUserNotificationsWithFilter(userId, filter);

        assertThat(result.getContent()).isEmpty();
        verify(notificationRepository).findByUserId(userId, pageable);
    }

    @Test
    void markAsRead_shouldMarkNotificationAsRead_whenUserIsOwner() {
        Long userId = 10L;
        Long notificationId = 1L;
        Notification notification = notification(notificationId, userId, Notification.Type.SYSTEM.getValue());
        NotificationResponse response = response(notificationId, userId, Notification.Type.SYSTEM.getValue());

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationMapper.toResponse(notification)).thenReturn(response);

        NotificationResponse result = notificationService.markAsRead(notificationId, userId);

        assertThat(result).isEqualTo(response);
        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_shouldThrowUnauthorizedAccessException_whenNotificationBelongsToAnotherUser() {
        Long ownerId = 10L;
        Long otherUserId = 99L;
        Long notificationId = 1L;
        Notification notification = notification(notificationId, ownerId, Notification.Type.SYSTEM.getValue());

        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, otherUserId))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_shouldThrowNotificationNotFoundException_whenNotificationDoesNotExist() {
        Long notificationId = 404L;
        Long userId = 10L;
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
                .isInstanceOf(NotificationNotFoundException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllAsRead_shouldMarkAllUnreadNotifications() {
        Long userId = 10L;
        Notification first = notification(1L, userId, Notification.Type.SYSTEM.getValue());
        Notification second = notification(2L, userId, Notification.Type.REVIEW.getValue());
        when(notificationRepository.findByUserIdAndIsReadFalse(userId)).thenReturn(List.of(first, second));

        notificationService.markAllAsRead(userId);

        assertThat(first.getIsRead()).isTrue();
        assertThat(second.getIsRead()).isTrue();
        verify(notificationRepository).saveAll(List.of(first, second));
    }

    @Test
    void markAllAsRead_shouldDoNothing_whenThereAreNoUnreadNotifications() {
        Long userId = 10L;
        when(notificationRepository.findByUserIdAndIsReadFalse(userId)).thenReturn(List.of());

        notificationService.markAllAsRead(userId);

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void deleteNotification_shouldDeleteNotification_whenUserIsOwner() {
        Long userId = 10L;
        Long notificationId = 1L;
        Notification notification = notification(notificationId, userId, Notification.Type.SYSTEM.getValue());
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        notificationService.deleteNotification(notificationId, userId);

        verify(notificationRepository).delete(notification);
    }

    @Test
    void deleteNotification_shouldThrowUnauthorizedAccessException_whenNotificationBelongsToAnotherUser() {
        Long ownerId = 10L;
        Long otherUserId = 99L;
        Long notificationId = 1L;
        Notification notification = notification(notificationId, ownerId, Notification.Type.SYSTEM.getValue());
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.deleteNotification(notificationId, otherUserId))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    @Test
    void deleteAllUserNotifications_shouldDeleteByUserId() {
        Long userId = 10L;

        notificationService.deleteAllUserNotifications(userId);

        verify(notificationRepository).deleteByUserId(userId);
    }

    @Test
    void deleteScheduledRouteNotifications_shouldDeleteOnlyRouteScheduledTypes() {
        Long routeId = 55L;

        notificationService.deleteScheduledRouteNotifications(routeId);

        verify(notificationRepository).deleteByRouteIdAndTypes(
                eq(routeId),
                eq(List.of(Notification.Type.ROUTE_DAY_START.getValue(), Notification.Type.ROUTE_REMINDER.getValue()))
        );
    }

    @Test
    void getUserNotificationStats_shouldBuildStatsByType() {
        Long userId = 10L;
        when(notificationRepository.countByUserId(userId)).thenReturn(10L);
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(3L);
        when(notificationRepository.countByUserIdGroupByType(userId)).thenReturn(List.of(
                new Object[]{Notification.Type.ROUTE_REMINDER.getValue(), 4L},
                new Object[]{Notification.Type.REVIEW.getValue(), 2L},
                new Object[]{Notification.Type.MODERATION.getValue(), 1L},
                new Object[]{Notification.Type.POI_UPDATE.getValue(), 3L}
        ));

        NotificationStatsResponse result = notificationService.getUserNotificationStats(userId);

        assertThat(result.getTotalCount()).isEqualTo(10L);
        assertThat(result.getUnreadCount()).isEqualTo(3L);
        assertThat(result.getRouteRemindersCount()).isEqualTo(4L);
        assertThat(result.getReviewNotificationsCount()).isEqualTo(2L);
        assertThat(result.getModerationNotificationsCount()).isEqualTo(1L);
        assertThat(result.getPoiUpdateNotificationsCount()).isEqualTo(3L);
    }

    @Test
    void getUserNotificationStats_shouldUseZeroForMissingTypes() {
        Long userId = 10L;
        when(notificationRepository.countByUserId(userId)).thenReturn(1L);
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(1L);
        when(notificationRepository.countByUserIdGroupByType(userId)).thenReturn(
                List.<Object[]>of(new Object[]{Notification.Type.SYSTEM.getValue(), 1L})
        );

        NotificationStatsResponse result = notificationService.getUserNotificationStats(userId);

        assertThat(result.getRouteRemindersCount()).isZero();
        assertThat(result.getReviewNotificationsCount()).isZero();
        assertThat(result.getModerationNotificationsCount()).isZero();
        assertThat(result.getPoiUpdateNotificationsCount()).isZero();
    }

    @Test
    void sendScheduledNotifications_shouldReturnEmptyList_whenThereAreNoReadyNotifications() {
        when(notificationRepository.findScheduledReady(any(LocalDateTime.class))).thenReturn(List.of());

        List<NotificationResponse> result = notificationService.sendScheduledNotifications();

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).saveAll(any());
        verify(emailService, never()).sendNotificationEmail(any());
    }

    @Test
    void sendScheduledNotifications_shouldMarkReadyNotificationsAsSentAndSendEmailForRouteReminder() {
        Long userId = 10L;
        Notification notification = notification(1L, userId, Notification.Type.ROUTE_REMINDER.getValue());
        notification.setScheduledAt(LocalDateTime.now().minusMinutes(1));
        NotificationResponse response = response(1L, userId, Notification.Type.ROUTE_REMINDER.getValue());

        when(notificationRepository.findScheduledReady(any(LocalDateTime.class))).thenReturn(List.of(notification));
        when(notificationRepository.saveAll(List.of(notification))).thenReturn(List.of(notification));
        when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

        List<NotificationResponse> result = notificationService.sendScheduledNotifications();

        assertThat(result).containsExactly(response);
        assertThat(notification.getStatus()).isEqualTo("SENT");
        assertThat(notification.getSentAt()).isNotNull();
        verify(emailService).sendNotificationEmail(any(NotificationResponse.class));
        verify(notificationRepository).saveAll(List.of(notification));
    }

    @Test
    void sendNotificationImmediately_shouldDelegateToEmailService() {
        NotificationResponse notification = response(1L, 10L, Notification.Type.SYSTEM.getValue());

        notificationService.sendNotificationImmediately(notification);

        verify(emailService).sendNotificationEmail(notification);
    }

    @Test
    void sendBatchNotifications_shouldCreateEachNotificationFromRequestList() {
        CreateNotificationRequest first = validRequest(Notification.Type.REVIEW.getValue());
        CreateNotificationRequest second = validRequest(Notification.Type.MODERATION.getValue());
        second.setTitle("Модерация");
        Notification firstNotification = notification(1L, first.getUserId(), first.getType());
        Notification secondNotification = notification(2L, second.getUserId(), second.getType());

        when(authClient.exists(10L)).thenReturn(true);
        when(notificationMapper.toEntity(first)).thenReturn(firstNotification);
        when(notificationMapper.toEntity(second)).thenReturn(secondNotification);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationMapper.toResponse(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            return response(notification.getId(), notification.getUserId(), notification.getType());
        });

        notificationService.sendBatchNotifications(List.of(first, second));

        verify(notificationMapper).toEntity(first);
        verify(notificationMapper).toEntity(second);
        verify(notificationRepository, times(4)).save(any(Notification.class));
    }

    private CreateNotificationRequest validRequest(String type) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setType(type);
        request.setTitle("Новое уведомление");
        request.setDescription("Описание уведомления");
        request.setUserId(10L);
        request.setDeliveryChannel("IN_APP");
        return request;
    }

    private Notification notification(Long id, Long userId, String type) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title("Новое уведомление")
                .description("Описание уведомления")
                .isRead(false)
                .userId(userId)
                .deliveryChannel("IN_APP")
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    private NotificationResponse response(Long id, Long userId, String type) {
        return NotificationResponse.builder()
                .id(id)
                .type(type)
                .title("Новое уведомление")
                .description("Описание уведомления")
                .isRead(false)
                .userId(userId)
                .deliveryChannel("IN_APP")
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }
}
