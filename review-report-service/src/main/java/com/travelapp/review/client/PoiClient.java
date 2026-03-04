package com.travelapp.review.client;

import com.travelapp.review.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "poi-service", url = "${service.poi.url}", configuration = FeignClientConfig.class)
public interface PoiClient {

    @GetMapping("/internal/{poiId}/exists")
    Boolean checkPoiExists(@PathVariable("poiId") Long poiId);

    @PutMapping("/internal/{poiId}/rating")
    void updatePoiRating(@PathVariable("poiId") Long poiId,
                         @RequestBody Map<String, Object> ratingUpdate);

    @PostMapping("/internal/{poiId}/report")
    void notifyPoiIssue(@PathVariable("poiId") Long poiId,
                        @RequestBody Map<String, Object> reportData);

    @GetMapping("/internal/{poiId}/info")
    Map<String, Object> getPoiInfo(@PathVariable("poiId") Long poiId);
}