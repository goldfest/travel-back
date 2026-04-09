package com.travelapp.poi.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelapp.poi.model.entity.PoiMedia;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PoiSearchRequest {

    @NotNull(message = "City ID is required")
    private Long cityId;

    private String searchQuery;

    private List<Long> poiTypeIds;

    @DecimalMin(value = "0.0", message = "Min rating must be between 0 and 5")
    @DecimalMax(value = "5.0", message = "Min rating must be between 0 and 5")
    private BigDecimal minRating;

    @Min(value = 0, message = "Min price must be between 0 and 4")
    @Max(value = 4, message = "Min price must be between 0 and 4")
    private Short minPrice;

    @Min(value = 0, message = "Max price must be between 0 and 4")
    @Max(value = 4, message = "Max price must be between 0 and 4")
    private Short maxPrice;

    private Boolean verifiedOnly = true;

    private Boolean excludeClosed = true;

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal userLat;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal userLng;

    @Min(value = 1, message = "Radius must be at least 1 km")
    @Max(value = 100, message = "Radius cannot exceed 100 km")
    private Integer radiusKm;

    @JsonProperty("features")
    private List<String> requiredFeatures;

    @Min(value = 1, message = "Page must be at least 1")
    private Integer page = 1;

    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 100, message = "Page size cannot exceed 100")
    private Integer size = 20;

    private String sortBy = "name";

    private Sort.Direction sortDirection = Sort.Direction.ASC;

    // Sorting options
    public enum SortField {
        NAME("name"),
        RATING("averageRating"),
        PRICE("priceLevel"),
        DISTANCE("distance");

        private final String fieldName;

        SortField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }
}