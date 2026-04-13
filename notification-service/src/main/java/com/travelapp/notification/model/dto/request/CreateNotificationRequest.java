package com.travelapp.notification.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Запрос на создание уведомления")
public class CreateNotificationRequest {

    @NotBlank(message = "Тип уведомления обязателен")
    @Size(max = 50, message = "Тип уведомления не должен превышать 50 символов")
    private String type;

    @NotBlank(message = "Заголовок уведомления обязателен")
    @Size(max = 255, message = "Заголовок уведомления не должен превышать 255 символов")
    private String title;

    @Size(max = 500, message = "Описание уведомления не должно превышать 500 символов")
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    private Long routeId;
    private Long routeDayId;
    private Long poiId;

    @NotNull(message = "ID пользователя обязателен")
    private Long userId;

    @Size(max = 120, message = "eventKey не должен превышать 120 символов")
    private String eventKey;

    @Size(max = 20, message = "deliveryChannel не должен превышать 20 символов")
    private String deliveryChannel;
}