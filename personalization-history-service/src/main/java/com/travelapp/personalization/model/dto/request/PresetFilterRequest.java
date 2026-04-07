package com.travelapp.personalization.model.dto.request;

import com.travelapp.personalization.validation.ValidJson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PresetFilterRequest {

    @NotBlank(message = "Filter name is required")
    @Size(min = 1, max = 100, message = "Filter name must be between 1 and 100 characters")
    private String name;

    @ValidJson
    @NotNull(message = "Filters JSON is required")
    private String filtersJson;

    @NotNull(message = "City ID is required")
    private Long cityId;

    @NotNull(message = "POI Type ID is required")
    private Long poiTypeId;
}