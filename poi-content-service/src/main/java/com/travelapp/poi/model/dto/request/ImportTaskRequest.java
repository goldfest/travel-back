package com.travelapp.poi.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ImportTaskRequest {

    @NotBlank(message = "Source code is required")
    private String sourceCode;

    @NotBlank(message = "Query is required")
    private String query;

    private Long cityId;

    private String importConfig;
}