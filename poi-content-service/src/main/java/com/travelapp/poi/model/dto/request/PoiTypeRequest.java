package com.travelapp.poi.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PoiTypeRequest {

    @NotBlank(message = "POI type code is required")
    @Size(max = 32, message = "Code must be less than 32 characters")
    private String code;

    @NotBlank(message = "POI type name is required")
    @Size(max = 64, message = "Name must be less than 64 characters")
    private String name;

    @Size(max = 120, message = "Icon URL must be less than 120 characters")
    private String icon;
}