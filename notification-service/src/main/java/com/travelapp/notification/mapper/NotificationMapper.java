package com.travelapp.notification.mapper;

import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.entity.Notification;
import org.mapstruct.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    // Маппим только то, что реально есть в CreateNotificationRequest
    @Mapping(target = "isRead", constant = "false")
    @Mapping(target = "sentAt", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    Notification toEntity(CreateNotificationRequest request);

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);

    // Страховка: если вдруг isRead не проставился
    @AfterMapping
    default void afterToEntity(@MappingTarget Notification notification) {
        if (notification.getIsRead() == null) {
            notification.setIsRead(false);
        }
        // createdAt/updatedAt выставит auditing, а sentAt/readAt — бизнес-логика
    }
}