package com.travelapp.poi.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ImportTaskRequest {

    @NotBlank(message = "Source code is required")
    @Pattern(
            regexp = "^(2gis|wiki|wikipedia)$",
            message = "Supported sourceCode values: 2gis, wiki, wikipedia"
    )
    private String sourceCode;

    @NotBlank(message = "Query is required")
    private String query;

    private Long cityId;
}