package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.service.GraphImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportAsyncService {

    private final GraphImportService graphImportService;

    @Async
    public void importCityGraphAsync(GraphImportRequest request) {
        try {
            graphImportService.importCityGraph(request);
        } catch (Exception ex) {
            log.error("Async graph import failed for cityId={}", request.getCityId(), ex);
        }
    }
}
