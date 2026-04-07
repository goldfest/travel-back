package com.travelapp.graphimport.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GraphImportStatsResponse {
    private Long cityId;
    private Long activeGraphVersionId;
    private Integer activeGraphVersionNo;
    private String activeGraphStatus;
    private LocalDateTime importedAt;
    private Long nodeCount;
    private Long edgeCount;
    private Long bindingCount;
    private String failureReason;
}
