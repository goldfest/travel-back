package com.travelapp.poi.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PoiUpdateRequest {

    @Size(max = 200, message = "Name must be less than 200 characters")
    private String name;

    @Size(max = 255, message = "Slug must be less than 255 characters")
    private String slug;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

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

    private Boolean isVerified;

    private Boolean isClosed;

    private JsonNode tags;

    @JsonProperty("features")
    private Map<String, String> features;
}