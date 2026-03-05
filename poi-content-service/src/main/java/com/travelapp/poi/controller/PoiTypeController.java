package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.response.PoiTypeResponse;
import com.travelapp.poi.security.SecurityUtils;
import com.travelapp.poi.service.PoiTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/poi-types")
@RequiredArgsConstructor
@Tag(name = "POI Type Management", description = "Endpoints for managing POI types")
public class PoiTypeController {

    private final PoiTypeService poiTypeService;

    @PostMapping
    @Operation(summary = "Create POI type", description = "Creates a new POI type (admin only)")
    public ResponseEntity<PoiTypeResponse> createPoiType(@Valid @RequestBody PoiTypeRequest request) {
        Long userId = SecurityUtils.requireUserId();
        PoiTypeResponse response = poiTypeService.createPoiType(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update POI type", description = "Updates an existing POI type (admin only)")
    public ResponseEntity<PoiTypeResponse> updatePoiType(
            @PathVariable Long id,
            @Valid @RequestBody PoiTypeRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(poiTypeService.updatePoiType(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete POI type", description = "Deletes a POI type (admin only)")
    public ResponseEntity<Void> deletePoiType(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        poiTypeService.deletePoiType(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get POI type by ID", description = "Retrieves a POI type by its ID")
    public ResponseEntity<PoiTypeResponse> getPoiTypeById(@PathVariable Long id) {
        return ResponseEntity.ok(poiTypeService.getPoiTypeById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get POI type by code", description = "Retrieves a POI type by its code")
    public ResponseEntity<PoiTypeResponse> getPoiTypeByCode(@PathVariable String code) {
        return ResponseEntity.ok(poiTypeService.getPoiTypeByCode(code));
    }

    @GetMapping("/all")
    @Operation(summary = "Get all POI types", description = "Retrieves all POI types")
    public ResponseEntity<List<PoiTypeResponse>> getAllPoiTypes() {
        return ResponseEntity.ok(poiTypeService.getAllPoiTypes());
    }

    @GetMapping
    @Operation(summary = "Get POI types", description = "Retrieves POI types with pagination")
    public ResponseEntity<Page<PoiTypeResponse>> getPoiTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(poiTypeService.getPoiTypes(pageable));
    }

    @PostMapping("/by-codes")
    @Operation(summary = "Get POI types by codes", description = "Retrieves POI types by their codes")
    public ResponseEntity<List<PoiTypeResponse>> getPoiTypesByCodes(@RequestBody List<String> codes) {
        return ResponseEntity.ok(poiTypeService.getPoiTypesByCodes(codes));
    }
}