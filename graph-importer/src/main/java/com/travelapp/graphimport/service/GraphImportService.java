package com.travelapp.graphimport.service;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.model.dto.response.GraphImportStatsResponse;

public interface GraphImportService {
    Long importCityGraph(GraphImportRequest request);
    GraphImportStatsResponse getCityImportStats(Long cityId);
}
