package com.travelapp.route.client;

import com.travelapp.route.model.dto.notification.CreateNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "notification-service",
        url = "${services.notification.url:http://notification-service:8086/api/notifications}",
        configuration = com.travelapp.route.config.FeignConfig.class
)
public interface NotificationClient {

    @PostMapping("/internal/notifications")
    ResponseEntity<Void> createNotification(@RequestBody CreateNotificationRequest request);

    @PostMapping("/internal/notifications/batch")
    ResponseEntity<Void> createBatchNotifications(@RequestBody List<CreateNotificationRequest> requests);

    @DeleteMapping("/internal/notifications/routes/{routeId}/scheduled")
    ResponseEntity<Void> deleteRouteScheduledNotifications(@PathVariable("routeId") Long routeId);
}
