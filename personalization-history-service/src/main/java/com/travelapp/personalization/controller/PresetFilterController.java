package com.travelapp.personalization.controller;

import com.travelapp.personalization.model.dto.request.PresetFilterRequest;
import com.travelapp.personalization.model.dto.response.PresetFilterResponse;
import com.travelapp.personalization.service.PresetFilterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/preset-filters")
@RequiredArgsConstructor
@Tag(name = "Preset Filter Management", description = "APIs for managing preset filters")
public class PresetFilterController {

    private final PresetFilterService presetFilterService;

    @PostMapping
    @Operation(summary = "Create a new preset filter")
    public ResponseEntity<PresetFilterResponse> createPresetFilter(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PresetFilterRequest request) {

        PresetFilterResponse response = presetFilterService.createPresetFilter(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{filterId}")
    @Operation(summary = "Update preset filter")
    public ResponseEntity<PresetFilterResponse> updatePresetFilter(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long filterId,
            @Valid @RequestBody PresetFilterRequest request) {

        PresetFilterResponse response = presetFilterService.updatePresetFilter(userId, filterId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{filterId}")
    @Operation(summary = "Delete preset filter")
    public ResponseEntity<Void> deletePresetFilter(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long filterId) {

        presetFilterService.deletePresetFilter(userId, filterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{filterId}")
    @Operation(summary = "Get preset filter by ID")
    public ResponseEntity<PresetFilterResponse> getPresetFilter(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long filterId) {

        PresetFilterResponse response = presetFilterService.getPresetFilter(userId, filterId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get user preset filters")
    public ResponseEntity<Page<PresetFilterResponse>> getUserPresetFilters(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<PresetFilterResponse> filters = presetFilterService.getUserPresetFilters(userId, pageable);
        return ResponseEntity.ok(filters);
    }

    @GetMapping("/context")
    @Operation(summary = "Get preset filters for specific context")
    public ResponseEntity<List<PresetFilterResponse>> getPresetFiltersForContext(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long cityId,
            @RequestParam Long poiTypeId) {

        List<PresetFilterResponse> filters = presetFilterService.getPresetFiltersForContext(
                userId, cityId, poiTypeId);
        return ResponseEntity.ok(filters);
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get preset filter by name")
    public ResponseEntity<PresetFilterResponse> getPresetFilterByName(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String name) {

        PresetFilterResponse response = presetFilterService.getPresetFilterByName(userId, name);
        return ResponseEntity.ok(response);
    }
}