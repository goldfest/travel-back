package com.travelapp.route.controller;

import com.travelapp.route.service.impl.GraphRoutingServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/graph-cache")
@RequiredArgsConstructor
public class GraphCacheInternalController {

    private final GraphRoutingServiceImpl graphRoutingService;

    @PostMapping("/cities/{cityId}/evict")
    public ResponseEntity<Map<String, Object>> evictCityGraphCache(@PathVariable Long cityId) {
        graphRoutingService.evictRoadGraphCache();
        return ResponseEntity.ok(Map.of("status", "OK", "cityId", cityId));
    }
}
