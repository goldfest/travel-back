package com.travelapp.notification.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.travelapp.notification.model.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ с данными уведомления")
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    private Boolean isRead;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime readAt;

    private Long routeId;
    private Long routeDayId;
    private Long poiId;
    private Long userId;
    private String eventKey;
    private String deliveryChannel;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .description(notification.getDescription())
                .scheduledAt(notification.getScheduledAt())
                .isRead(notification.getIsRead())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .routeId(notification.getRouteId())
                .routeDayId(notification.getRouteDayId())
                .poiId(notification.getPoiId())
                .userId(notification.getUserId())
                .eventKey(notification.getEventKey())
                .deliveryChannel(notification.getDeliveryChannel())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}