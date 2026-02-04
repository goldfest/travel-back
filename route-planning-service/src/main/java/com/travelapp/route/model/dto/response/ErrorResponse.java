package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "Ответ с информацией об ошибке")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @Schema(description = "Временная метка ошибки")
    private LocalDateTime timestamp;

    @Schema(description = "HTTP статус код", example = "400")
    private Integer status;

    @Schema(description = "Текст ошибки", example = "Bad Request")
    private String error;

    @Schema(description = "Сообщение об ошибке", example = "Invalid request parameters")
    private String message;

    @Schema(description = "Детали ошибки (опционально)")
    private Map<String, String> details;

    @Schema(description = "Путь запроса", example = "/api/v1/routes")
    private String path;
}