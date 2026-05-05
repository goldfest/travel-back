package com.travelapp.notification.model;

import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.entity.Notification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEntityTest {

    @Test
    void prePersist_shouldSetDefaultValues_whenOptionalStateFieldsAreNull() {
        Notification notification = Notification.builder()
                .type(Notification.Type.SYSTEM.getValue())
                .title("Системное уведомление")
                .userId(10L)
                .isRead(null)
                .deliveryChannel(null)
                .status(null)
                .build();

        notification.prePersist();

        assertThat(notification.getIsRead()).isFalse();
        assertThat(notification.getDeliveryChannel()).isEqualTo("IN_APP");
        assertThat(notification.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void prePersist_shouldSetDefaultValues_whenOptionalStateFieldsAreBlank() {
        Notification notification = Notification.builder()
                .type(Notification.Type.SYSTEM.getValue())
                .title("Системное уведомление")
                .userId(10L)
                .deliveryChannel("   ")
                .status("   ")
                .build();

        notification.prePersist();

        assertThat(notification.getDeliveryChannel()).isEqualTo("IN_APP");
        assertThat(notification.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void markAsRead_shouldSetReadFlagAndReadAt() {
        Notification notification = Notification.builder()
                .type(Notification.Type.SYSTEM.getValue())
                .title("Системное уведомление")
                .userId(10L)
                .isRead(false)
                .build();

        notification.markAsRead();

        assertThat(notification.getIsRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void markAsSent_shouldSetStatusSentAndSentAt() {
        Notification notification = Notification.builder()
                .type(Notification.Type.ROUTE_REMINDER.getValue())
                .title("Напоминание")
                .userId(10L)
                .status("PENDING")
                .build();

        notification.markAsSent();

        assertThat(notification.getStatus()).isEqualTo("SENT");
        assertThat(notification.getSentAt()).isNotNull();
    }

    @Test
    void markAsFailed_shouldSetFailedStatus() {
        Notification notification = Notification.builder()
                .type(Notification.Type.ROUTE_REMINDER.getValue())
                .title("Напоминание")
                .userId(10L)
                .status("PENDING")
                .build();

        notification.markAsFailed();

        assertThat(notification.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void notificationResponseFromEntity_shouldCopyEntityFields() {
        LocalDateTime scheduledAt = LocalDateTime.now().plusHours(1);
        LocalDateTime sentAt = LocalDateTime.now();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        Notification notification = Notification.builder()
                .id(1L)
                .type(Notification.Type.ROUTE_REMINDER.getValue())
                .title("Напоминание о маршруте")
                .description("Маршрут скоро начнётся")
                .scheduledAt(scheduledAt)
                .sentAt(sentAt)
                .isRead(false)
                .routeId(100L)
                .routeDayId(200L)
                .poiId(300L)
                .userId(10L)
                .eventKey("route-100-reminder")
                .deliveryChannel("EMAIL")
                .status("SENT")
                .createdAt(createdAt)
                .build();

        NotificationResponse response = NotificationResponse.fromEntity(notification);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getType()).isEqualTo(Notification.Type.ROUTE_REMINDER.getValue());
        assertThat(response.getTitle()).isEqualTo("Напоминание о маршруте");
        assertThat(response.getDescription()).isEqualTo("Маршрут скоро начнётся");
        assertThat(response.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(response.getSentAt()).isEqualTo(sentAt);
        assertThat(response.getIsRead()).isFalse();
        assertThat(response.getRouteId()).isEqualTo(100L);
        assertThat(response.getRouteDayId()).isEqualTo(200L);
        assertThat(response.getPoiId()).isEqualTo(300L);
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getEventKey()).isEqualTo("route-100-reminder");
        assertThat(response.getDeliveryChannel()).isEqualTo("EMAIL");
        assertThat(response.getStatus()).isEqualTo("SENT");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }
}
