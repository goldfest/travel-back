package com.travelapp.city.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "DTO для ответа с информацией о городе")
public class CityResponseDto {

    @Schema(description = "ID города", example = "1")
    private Long id;

    @Schema(description = "Название города", example = "Москва")
    private String name;

    @Schema(description = "Название страны", example = "Россия")
    private String country;

    @Schema(description = "Описание города", example = "Столица России, крупнейший город страны")
    private String description;

    @Schema(description = "Широта центра города", example = "55.755826")
    private BigDecimal centerLat;

    @Schema(description = "Долгота центра города", example = "37.617300")
    private BigDecimal centerLng;

    @Schema(description = "Признак популярного города", example = "true")
    private Boolean isPopular;

    @Schema(description = "URL-идентификатор города", example = "moscow")
    private String slug;

    @Schema(description = "Код страны по ISO 3166-1 alpha-2", example = "RU")
    private String countryCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Дата создания", example = "2024-01-01 12:00:00")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "Дата последнего обновления", example = "2024-01-02 12:00:00")
    private LocalDateTime updatedAt;
}