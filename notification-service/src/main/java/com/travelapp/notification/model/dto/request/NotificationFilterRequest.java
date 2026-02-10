// notification-service/src/main/java/com/travelapp/notification/model/dto/request/NotificationFilterRequest.java
package com.travelapp.notification.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Фильтр для поиска уведомлений")
public class NotificationFilterRequest {

    @Schema(description = "Тип уведомления", example = "route_reminder")
    private String type;

    @Schema(description = "Статус прочтения", example = "false")
    private Boolean isRead;

    @Schema(description = "ID пользователя", example = "1")
    private Long userId;

    @Schema(description = "Номер страницы", example = "0", defaultValue = "0")
    private Integer page = 0;

    @Schema(description = "Размер страницы", example = "20", defaultValue = "20")
    private Integer size = 20;

    @Schema(description = "Поле для сортировки", example = "createdAt", defaultValue = "createdAt")
    private String sortBy = "createdAt";

    @Schema(description = "Направление сортировки", example = "DESC", defaultValue = "DESC")
    private Sort.Direction direction = Sort.Direction.DESC;

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}