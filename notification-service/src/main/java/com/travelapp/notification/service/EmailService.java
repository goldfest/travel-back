// notification-service/src/main/java/com/travelapp/notification/service/EmailService.java
package com.travelapp.notification.service;

import com.travelapp.notification.model.dto.response.NotificationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

public interface EmailService {

    @CircuitBreaker(name = "mailService", fallbackMethod = "sendNotificationEmailFallback")
    void sendNotificationEmail(NotificationResponse notification);

    void sendNotificationEmailFallback(NotificationResponse notification, Throwable throwable);
}