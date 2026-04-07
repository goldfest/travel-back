package com.travelapp.graphimport.controller;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.model.dto.response.GraphImportStatsResponse;
import com.travelapp.graphimport.service.GraphImportService;
import com.travelapp.graphimport.service.impl.GraphImportAsyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/graph-import")
@RequiredArgsConstructor
public class GraphImportController {

    private final GraphImportService graphImportService;
    private final GraphImportAsyncService graphImportAsyncService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> importCityGraph(@Valid @RequestBody GraphImportRequest request) {
        graphImportAsyncService.importCityGraphAsync(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "STARTED", "cityId", request.getCityId()));
    }

    @GetMapping("/cities/{cityId}/stats")
    public ResponseEntity<GraphImportStatsResponse> getCityImportStats(@PathVariable Long cityId) {
        return ResponseEntity.ok(graphImportService.getCityImportStats(cityId));
    }
}
