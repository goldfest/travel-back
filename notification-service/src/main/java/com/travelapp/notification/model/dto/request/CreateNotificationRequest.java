// notification-service/src/main/java/com/travelapp/notification/model/dto/request/CreateNotificationRequest.java
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
    @Size(max = 24, message = "Тип уведомления не должен превышать 24 символа")
    @Schema(description = "Тип уведомления", example = "route_reminder", required = true)
    private String type;

    @NotBlank(message = "Заголовок уведомления обязателен")
    @Size(max = 160, message = "Заголовок уведомления не должен превышать 160 символов")
    @Schema(description = "Заголовок уведомления", example = "Напоминание о маршруте", required = true)
    private String title;

    @Size(max = 500, message = "Описание уведомления не должно превышать 500 символов")
    @Schema(description = "Описание уведомления", example = "Ваш маршрут начнется через 2 часа")
    private String description;

    @Schema(description = "Время запланированной отправки", example = "2024-12-31T10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;

    @Schema(description = "ID маршрута", example = "1")
    private Long routeId;

    @Schema(description = "ID объекта интереса", example = "123")
    private Long poiId;

    @NotNull(message = "ID пользователя обязателен")
    @Schema(description = "ID пользователя", example = "1", required = true)
    private Long userId;
}