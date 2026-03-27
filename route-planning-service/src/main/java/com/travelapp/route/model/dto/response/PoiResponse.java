package com.travelapp.route.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Ответ с информацией о POI")
public class PoiResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String phone;

    @JsonProperty("siteUrl")
    private String siteUrl;

    @JsonProperty("priceLevel")
    private Short priceLevel;

    @JsonProperty("cityId")
    private Long cityId;

    @JsonProperty("isVerified")
    private Boolean isVerified;

    @JsonProperty("isClosed")
    private Boolean isClosed;

    @JsonProperty("coverUrl")
    private String coverUrl;

    @JsonProperty("poiType")
    private PoiTypeDto poiType;

    @Data
    public static class PoiTypeDto {
        private Long id;
        private String code;
        private String name;
        private String icon;
    }
}