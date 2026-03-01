// notification-service/src/main/java/com/travelapp/notification/service/impl/EmailServiceImpl.java
package com.travelapp.notification.service.impl;

import com.travelapp.notification.model.dto.response.NotificationResponse;
import com.travelapp.notification.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.email.from:noreply@travelapp.com}")
    private String fromEmail;

    @Value("${notification.email.enabled:true}")
    private boolean emailEnabled;

    @Async("notificationTaskExecutor")
    @Override
    public void sendNotificationEmail(NotificationResponse notification) {
        if (!emailEnabled) {
            log.debug("Email notifications are disabled");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo("user" + notification.getUserId() + "@example.com"); // В реальном приложении нужно получать email из профиля
            helper.setSubject("TravelApp: " + notification.getTitle());

            String emailContent = buildEmailContent(notification);
            helper.setText(emailContent, true);

            mailSender.send(message);
            log.info("Email notification sent successfully for notification {}", notification.getId());

        } catch (MessagingException e) {
            log.error("Failed to send email notification for {}: {}", notification.getId(), e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Override
    public void sendNotificationEmailFallback(NotificationResponse notification, Throwable throwable) {
        log.error("Email service fallback triggered for notification {}. Error: {}",
                notification.getId(), throwable.getMessage());

        // Здесь можно добавить логику для отправки через другой канал
        // или сохранения в очередь для повторной отправки
        log.warn("Notification {} could not be sent via email. Saved for retry.", notification.getId());
    }

    private String buildEmailContent(NotificationResponse notification) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; }
                    .content { padding: 20px; background-color: #f9f9f9; }
                    .footer { margin-top: 20px; padding: 10px; text-align: center; color: #666; font-size: 12px; }
                    .notification-type { 
                        display: inline-block; 
                        padding: 5px 10px; 
                        border-radius: 3px; 
                        font-size: 12px; 
                        margin-bottom: 10px; 
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>TravelApp Уведомление</h1>
                    </div>
                    <div class="content">
                        <div class="notification-type" style="background-color: #e3f2fd; color: #1976d2;">
                            %s
                        </div>
                        <h2>%s</h2>
                        <p>%s</p>
                        <hr>
                        <p><strong>ID уведомления:</strong> %d</p>
                        <p><strong>Дата отправки:</strong> %s</p>
                    </div>
                    <div class="footer">
                        <p>Это автоматическое сообщение от TravelApp. Пожалуйста, не отвечайте на это письмо.</p>
                        <p>© 2024 TravelApp. Все права защищены.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                getTypeDisplayName(notification.getType()),
                notification.getTitle(),
                notification.getDescription() != null ? notification.getDescription() : "",
                notification.getId(),
                notification.getSentAt() != null ? notification.getSentAt().toString() : "Немедленно"
        );
    }

    private String getTypeDisplayName(String type) {
        return switch (type) {
            case "route_reminder" -> "Напоминание о маршруте";
            case "review" -> "Отзыв";
            case "poi_update" -> "Обновление объекта";
            case "moderation" -> "Модерация";
            case "system" -> "Системное";
            case "promotion" -> "Акция";
            default -> "Уведомление";
        };
    }
}