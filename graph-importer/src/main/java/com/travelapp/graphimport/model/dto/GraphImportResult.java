package com.travelapp.graphimport.model.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GraphImportResult {
    Long cityId;
    Long graphVersionId;
    Integer versionNo;
    String status;
    int importedNodes;
    int importedEdges;
    int boundPois;
    int skippedPois;
}
