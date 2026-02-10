package com.travelapp.poi.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PoiResponse {

    private Long id;
    private String name;
    private String slug;
    private JsonNode tags;
    private String description;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String phone;
    private String siteUrl;
    private Short priceLevel;
    private BigDecimal averageRating;
    private Integer ratingCount;
    private Boolean isVerified;
    private Boolean isClosed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonProperty("poiType")
    private PoiTypeResponse poiType;
    private Long cityId;
    private Long createdBy;

    @JsonProperty("features")
    private Map<String, String> features;

    @JsonProperty("hours")
    private List<PoiHoursResponse> hours;

    @JsonProperty("media")
    private List<PoiMediaResponse> media;

    @JsonProperty("sources")
    private List<PoiSourceResponse> sources;

    // Distance from user (if calculated)
    private Double distanceKm;

    // Current status (open/closed based on hours)
    private Boolean isOpenNow;
    private String currentStatus;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PoiTypeResponse {
        private Long id;
        private String code;
        private String name;
        private String icon;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PoiHoursResponse {
        private Short dayOfWeek;
        private String openTime;
        private String closeTime;
        private Boolean aroundTheClock;
        private Boolean isToday;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PoiMediaResponse {
        private Long id;
        private String url;
        private String mediaType;
        private String moderationStatus;
        private LocalDateTime createdAt;
        private Long userId;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PoiSourceResponse {
        private Long id;
        private String sourceCode;
        private String sourceUrl;
        private BigDecimal confidenceScore;
        private LocalDateTime createdAt;
    }
}