package com.travelapp.graphimport.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GraphImportRequest {
    @NotNull
    private Long cityId;
    @NotBlank
    private String osmFilePath;
}
