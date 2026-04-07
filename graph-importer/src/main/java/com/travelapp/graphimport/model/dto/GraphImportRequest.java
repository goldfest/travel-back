package com.travelapp.graphimport.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GraphImportRequest {
    @NotNull
    private Long cityId;
    private String osmFilePath;
}
