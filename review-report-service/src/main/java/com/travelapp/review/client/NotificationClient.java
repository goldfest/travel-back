package com.travelapp.review.client;

import com.travelapp.review.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "notification-service", url = "${service.notification.url}", configuration = FeignClientConfig.class)
public interface NotificationClient {

    @PostMapping("/internal/notifications/moderator")
    void sendModeratorNotification(
            @RequestParam("type") String type,
            @RequestBody Map<String, Object> data);

    @PostMapping("/internal/notifications/user/{userId}")
    void sendUserNotification(
            @PathVariable("userId") Long userId,
            @RequestParam("type") String type,
            @RequestBody Map<String, Object> data);
}