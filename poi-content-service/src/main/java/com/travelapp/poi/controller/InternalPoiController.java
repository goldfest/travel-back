package com.travelapp.poi.controller;

import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.exception.ResourceNotFoundException;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.repository.PoiRepository;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalPoiController {

    private final PoiRepository poiRepository;

    @GetMapping("/{poiId}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable Long poiId) {
        return ResponseEntity.ok(poiRepository.existsById(poiId));
    }

    @PutMapping("/{poiId}/rating")
    public ResponseEntity<Void> updateRating(
            @PathVariable Long poiId,
            @RequestBody Map<String, Object> request
    ) {
        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new PoiNotFoundException(poiId));

        Object averageRating = request.get("averageRating");
        Object ratingCount = request.get("ratingCount");

        if (averageRating != null) {
            poi.setAverageRating(toDouble(averageRating));
        }
        if (ratingCount != null) {
            poi.setRatingCount(toLong(ratingCount).intValue());
        }

        poiRepository.save(poi);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{poiId}/report")
    public ResponseEntity<Void> increaseReports(
            @PathVariable Long poiId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new PoiNotFoundException(poiId));

        Integer current = poi.getReportsCount() == null ? 0 : poi.getReportsCount();
        poi.setReportsCount(current + 1);
        poiRepository.save(poi);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/{poiId}/info")
    public ResponseEntity<Map<String, Object>> getInfo(@PathVariable Long poiId) {
        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new PoiNotFoundException(poiId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", poi.getId());
        response.put("name", poi.getName());
        response.put("description", poi.getDescription());
        response.put("cityId", poi.getCityId());
        response.put("averageRating", poi.getAverageRating());
        response.put("ratingCount", poi.getRatingCount());
        response.put("reportsCount", poi.getReportsCount());

        return ResponseEntity.ok(response);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}