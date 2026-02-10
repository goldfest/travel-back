package com.travelapp.city.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO для создания/обновления города")
public class CityRequestDto {

    @NotBlank(message = "Название города обязательно")
    @Size(max = 120, message = "Название города не должно превышать 120 символов")
    @Schema(description = "Название города", example = "Москва", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 100, message = "Название страны не должно превышать 100 символов")
    @Schema(description = "Название страны", example = "Россия")
    private String country;

    @Schema(description = "Описание города", example = "Столица России, крупнейший город страны")
    private String description;

    @NotNull(message = "Широта центра города обязательна")
    @Schema(description = "Широта центра города", example = "55.755826", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal centerLat;

    @NotNull(message = "Долгота центра города обязательна")
    @Schema(description = "Долгота центра города", example = "37.617300", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal centerLng;

    @Schema(description = "Признак популярного города", example = "true")
    private Boolean isPopular = false;

    @NotBlank(message = "Slug города обязателен")
    @Size(max = 120, message = "Slug не должен превышать 120 символов")
    @Schema(description = "URL-идентификатор города", example = "moscow", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slug;

    @Size(min = 2, max = 2, message = "Код страны должен состоять из 2 символов")
    @Schema(description = "Код страны по ISO 3166-1 alpha-2", example = "RU")
    private String countryCode;
}