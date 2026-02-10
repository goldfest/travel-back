package com.travelapp.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "poi-service", url = "${service.poi.url}")
public interface PoiClient {

    @GetMapping("/internal/{poiId}/exists")
    ResponseEntity<Boolean> checkPoiExists(@PathVariable("poiId") Long poiId);

    @PutMapping("/internal/{poiId}/rating")
    ResponseEntity<Void> updatePoiRating(
            @PathVariable("poiId") Long poiId,
            @RequestBody Map<String, Object> ratingUpdate);

    @PostMapping("/internal/{poiId}/report")
    ResponseEntity<Void> notifyPoiIssue(
            @PathVariable("poiId") Long poiId,
            @RequestBody Map<String, Object> reportData);

    @GetMapping("/internal/{poiId}/info")
    ResponseEntity<Map<String, Object>> getPoiInfo(@PathVariable("poiId") Long poiId);
}