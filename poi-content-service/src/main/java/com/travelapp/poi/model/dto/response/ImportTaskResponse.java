package com.travelapp.poi.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportTaskResponse {

    private Long id;
    private String sourceCode;
    private String query;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer totalPoiFound;
    private Integer totalPoiCreated;
    private Integer totalPoiUpdated;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long cityId;
}