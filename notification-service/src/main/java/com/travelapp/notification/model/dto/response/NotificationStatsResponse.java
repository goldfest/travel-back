// notification-service/src/main/java/com/travelapp/notification/model/dto/response/NotificationStatsResponse.java
package com.travelapp.notification.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Статистика по уведомлениям пользователя")
public class NotificationStatsResponse {

    @Schema(description = "Общее количество уведомлений", example = "150")
    private Long totalCount;

    @Schema(description = "Количество непрочитанных уведомлений", example = "15")
    private Long unreadCount;

    @Schema(description = "Количество уведомлений о маршрутах", example = "50")
    private Long routeRemindersCount;

    @Schema(description = "Количество уведомлений об отзывах", example = "30")
    private Long reviewNotificationsCount;

    @Schema(description = "Количество уведомлений о модерации", example = "20")
    private Long moderationNotificationsCount;

    @Schema(description = "Количество уведомлений об обновлениях объектов", example = "25")
    private Long poiUpdateNotificationsCount;
}