package com.travelapp.notification.mapper;

import com.travelapp.notification.model.dto.request.CreateNotificationRequest;
import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.model.entity.Notification;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "sentAt", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    @Mapping(target = "isRead", constant = "false")
    @Mapping(target = "deliveryChannel", source = "deliveryChannel", qualifiedByName = "mapDeliveryChannel")
    @Mapping(target = "status", expression = "java(\"PENDING\")")
    Notification toEntity(CreateNotificationRequest request);

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);

    @Named("mapDeliveryChannel")
    default String mapDeliveryChannel(String deliveryChannel) {
        return (deliveryChannel == null || deliveryChannel.isBlank()) ? "IN_APP" : deliveryChannel;
    }
}