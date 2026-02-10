package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.response.PoiTypeResponse;
import com.travelapp.poi.service.PoiTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/poi/poi-types")
@RequiredArgsConstructor
@Tag(name = "POI Type Management", description = "Endpoints for managing POI types")
public class PoiTypeController {

    private final PoiTypeService poiTypeService;

    @PostMapping
    @Operation(summary = "Create POI type", description = "Creates a new POI type (admin only)")
    public ResponseEntity<PoiTypeResponse> createPoiType(
            @Valid @RequestBody PoiTypeRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        PoiTypeResponse response = poiTypeService.createPoiType(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update POI type", description = "Updates an existing POI type (admin only)")
    public ResponseEntity<PoiTypeResponse> updatePoiType(
            @PathVariable Long id,
            @Valid @RequestBody PoiTypeRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        PoiTypeResponse response = poiTypeService.updatePoiType(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete POI type", description = "Deletes a POI type (admin only)")
    public ResponseEntity<Void> deletePoiType(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        poiTypeService.deletePoiType(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get POI type by ID", description = "Retrieves a POI type by its ID")
    public ResponseEntity<PoiTypeResponse> getPoiTypeById(@PathVariable Long id) {
        PoiTypeResponse response = poiTypeService.getPoiTypeById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get POI type by code", description = "Retrieves a POI type by its code")
    public ResponseEntity<PoiTypeResponse> getPoiTypeByCode(@PathVariable String code) {
        PoiTypeResponse response = poiTypeService.getPoiTypeByCode(code);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/all")
    @Operation(summary = "Get all POI types", description = "Retrieves all POI types")
    public ResponseEntity<List<PoiTypeResponse>> getAllPoiTypes() {
        List<PoiTypeResponse> response = poiTypeService.getAllPoiTypes();
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get POI types", description = "Retrieves POI types with pagination")
    public ResponseEntity<Page<PoiTypeResponse>> getPoiTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<PoiTypeResponse> response = poiTypeService.getPoiTypes(pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/by-codes")
    @Operation(summary = "Get POI types by codes", description = "Retrieves POI types by their codes")
    public ResponseEntity<List<PoiTypeResponse>> getPoiTypesByCodes(
            @RequestBody List<String> codes) {
        List<PoiTypeResponse> response = poiTypeService.getPoiTypesByCodes(codes);
        return ResponseEntity.ok(response);
    }
}