package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ответ с информацией о POI")
public class PoiResponse {

    @Schema(description = "ID объекта", example = "1")
    private Long id;

    @Schema(description = "Название объекта", example = "Эрмитаж")
    private String name;

    @Schema(description = "Slug для URL", example = "hermitage-museum")
    private String slug;

    @Schema(description = "Описание объекта")
    private String description;

    @Schema(description = "Адрес", example = "Дворцовая пл., 2")
    private String address;

    @Schema(description = "Широта", example = "59.9398")
    private Double latitude;

    @Schema(description = "Долгота", example = "30.3146")
    private Double longitude;

    @Schema(description = "Телефон", example = "+78127103434")
    private String phone;

    @Schema(description = "URL сайта", example = "https://hermitagemuseum.org")
    @JsonProperty("site_url")
    private String siteUrl;

    @Schema(description = "Уровень цен (0-4)", example = "3")
    @JsonProperty("price_level")
    private Short priceLevel;

    @Schema(description = "Средний рейтинг", example = "4.7")
    @JsonProperty("average_rating")
    private Double averageRating;

    @Schema(description = "Количество отзывов", example = "1250")
    @JsonProperty("rating_count")
    private Integer ratingCount;

    @Schema(description = "Тип объекта", example = "museum")
    private String type;

    @Schema(description = "ID города", example = "1")
    @JsonProperty("city_id")
    private Long cityId;

    @Schema(description = "Верифицирован ли объект", example = "true")
    @JsonProperty("is_verified")
    private Boolean isVerified;

    @Schema(description = "Закрыт ли объект", example = "false")
    @JsonProperty("is_closed")
    private Boolean isClosed;

    @Schema(description = "URL обложки")
    @JsonProperty("cover_url")
    private String coverUrl;
}