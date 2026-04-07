// notification-service/src/main/java/com/travelapp/notification/model/dto/response/NotificationResponse.java
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

    @Schema(description = "ID уведомления", example = "1")
    private Long id;

    @Schema(description = "Тип уведомления", example = "route_reminder")
    private String type;

    @Schema(description = "Заголовок уведомления", example = "Напоминание о маршруте")
    private String title;

    @Schema(description = "Описание уведомления", example = "Ваш маршрут начнется через 2 часа")
    private String description;

    @Schema(description = "Время запланированной отправки", example = "2024-12-31T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    @Schema(description = "Статус прочтения", example = "false")
    private Boolean isRead;

    @Schema(description = "Время отправки", example = "2024-12-31T09:55:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime sentAt;

    @Schema(description = "Время прочтения", example = "2024-12-31T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime readAt;

    @Schema(description = "ID маршрута", example = "1")
    private Long routeId;

    @Schema(description = "ID объекта интереса", example = "123")
    private Long poiId;

    @Schema(description = "ID пользователя", example = "1")
    private Long userId;

    @Schema(description = "Дата создания", example = "2024-12-31T09:50:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Дата обновления", example = "2024-12-31T09:55:00")
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
                .poiId(notification.getPoiId())
                .userId(notification.getUserId())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}