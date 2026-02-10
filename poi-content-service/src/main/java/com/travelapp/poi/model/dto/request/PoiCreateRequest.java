package com.travelapp.poi.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.model.entity.PoiHours;
import com.travelapp.poi.model.entity.PoiMedia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Data
public class PoiCreateRequest {

    @NotBlank(message = "POI name is required")
    @Size(max = 200, message = "Name must be less than 200 characters")
    private String name;

    @NotBlank(message = "Slug is required")
    @Size(max = 255, message = "Slug must be less than 255 characters")
    private String slug;

    @NotNull(message = "City ID is required")
    private Long cityId;

    @NotNull(message = "POI type ID is required")
    private Long poiTypeId;

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    @Size(max = 300, message = "Address must be less than 300 characters")
    private String address;

    @Size(max = 1000, message = "Description must be less than 1000 characters")
    private String description;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phone;

    @Size(max = 300, message = "Site URL must be less than 300 characters")
    private String siteUrl;

    @Min(value = 0, message = "Price level must be between 0 and 4")
    @Max(value = 4, message = "Price level must be between 0 and 4")
    private Short priceLevel;

    private JsonNode tags;

    @JsonProperty("features")
    private Map<String, String> features;

    @Valid
    private List<HoursRequest> hours;

    @Valid
    private List<MediaRequest> media;

    @Valid
    private List<SourceRequest> sources;

    @Data
    public static class HoursRequest {

        @NotNull(message = "Day of week is required")
        @Min(value = 0, message = "Day of week must be between 0 and 6")
        @Max(value = 6, message = "Day of week must be between 0 and 6")
        private Short dayOfWeek;

        private LocalTime openTime;

        private LocalTime closeTime;

        private Boolean aroundTheClock = false;
    }

    @Data
    public static class MediaRequest {

        @NotBlank(message = "Media URL is required")
        @Size(max = 500, message = "URL must be less than 500 characters")
        private String url;

        @NotNull(message = "Media type is required")
        private PoiMedia.MediaType mediaType;
    }

    @Data
    public static class SourceRequest {

        @NotBlank(message = "Source code is required")
        @Size(max = 32, message = "Source code must be less than 32 characters")
        private String sourceCode;

        @Size(max = 500, message = "Source URL must be less than 500 characters")
        private String sourceUrl;

        @DecimalMin(value = "0.0", message = "Confidence score must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Confidence score must be between 0 and 1")
        private BigDecimal confidenceScore;
    }
}