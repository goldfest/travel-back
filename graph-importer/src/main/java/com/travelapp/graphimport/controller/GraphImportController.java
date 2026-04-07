package com.travelapp.graphimport.controller;

import com.travelapp.graphimport.model.dto.GraphImportRequest;
import com.travelapp.graphimport.service.GraphImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/graph-import")
@RequiredArgsConstructor
public class GraphImportController {

    private final GraphImportService graphImportService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> importCityGraph(@Valid @RequestBody GraphImportRequest request) {
        Long versionId = graphImportService.importCityGraph(request);
        return ResponseEntity.ok(Map.of("status", "OK", "graphVersionId", versionId, "cityId", request.getCityId()));
    }
}
