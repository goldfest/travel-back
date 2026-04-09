package com.travelapp.route.model.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {
    private String type;
    private String title;
    private String description;
    private LocalDateTime scheduledAt;
    private Long routeId;
    private Long routeDayId;
    private Long poiId;
    private Long userId;
    private String eventKey;
    private String deliveryChannel;
}
