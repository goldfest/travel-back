package com.travelapp.graphimport.service;

import com.travelapp.graphimport.model.dto.GraphImportRequest;

public interface GraphImportService {
    Long importCityGraph(GraphImportRequest request);
}
