package com.travelapp.city.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Лёгкий DTO для выбора города (выпадашки/быстрые списки)")
public class CityLookupDto {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "Москва")
    private String name;

    @Schema(example = "Россия")
    private String country;

    @Schema(example = "moscow")
    private String slug;

    @Schema(example = "true")
    private Boolean isPopular;

    @Schema(example = "RU")
    private String countryCode;
}