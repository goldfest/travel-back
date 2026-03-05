package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.security.SecurityUtils;
import com.travelapp.poi.service.PoiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/pois")
@RequiredArgsConstructor
@Tag(name = "POI Management", description = "Endpoints for managing Points of Interest")
public class PoiController {

    private final PoiService poiService;

    @PostMapping
    @Operation(summary = "Create a new POI", description = "Creates a new point of interest")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "POI created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<PoiResponse> createPoi(@Valid @RequestBody PoiCreateRequest request) {
        Long userId = SecurityUtils.requireUserId();
        PoiResponse response = poiService.createPoi(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get POI by ID", description = "Retrieves a point of interest by its ID")
    public ResponseEntity<PoiResponse> getPoiById(@PathVariable Long id) {
        return ResponseEntity.ok(poiService.getPoiById(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get POI by slug", description = "Retrieves a point of interest by its slug")
    public ResponseEntity<PoiResponse> getPoiBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(poiService.getPoiBySlug(slug));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update POI", description = "Updates an existing point of interest")
    public ResponseEntity<PoiResponse> updatePoi(
            @PathVariable Long id,
            @Valid @RequestBody PoiUpdateRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();
        return ResponseEntity.ok(poiService.updatePoi(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete POI", description = "Deletes a point of interest")
    public ResponseEntity<Void> deletePoi(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        poiService.deletePoi(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    @Operation(summary = "Search POIs", description = "Searches for points of interest with filters")
    public ResponseEntity<Page<PoiResponse>> searchPois(@Valid @RequestBody PoiSearchRequest request) {
        return ResponseEntity.ok(poiService.searchPois(request));
    }

    @GetMapping("/city/{cityId}")
    @Operation(summary = "Get POIs by city", description = "Retrieves points of interest for a city")
    public ResponseEntity<Page<PoiResponse>> getPoisByCity(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction sortDirection
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        return ResponseEntity.ok(poiService.getPoisByCity(cityId, pageable));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get nearby POIs", description = "Retrieves nearby points of interest")
    public ResponseEntity<List<PoiResponse>> getNearbyPois(
            @RequestParam Long cityId,
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(defaultValue = "5") Integer radiusKm,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(poiService.getNearbyPois(cityId, lat, lng, radiusKm, limit));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify POI", description = "Marks a POI as verified (admin only)")
    public ResponseEntity<Void> verifyPoi(@PathVariable Long id) {
        Long adminId = SecurityUtils.requireUserId();
        poiService.verifyPoi(id, adminId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/unverify")
    @Operation(summary = "Unverify POI", description = "Marks a POI as unverified (admin only)")
    public ResponseEntity<Void> unverifyPoi(@PathVariable Long id) {
        Long adminId = SecurityUtils.requireUserId();
        poiService.unverifyPoi(id, adminId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unverified")
    @Operation(summary = "Get unverified POIs", description = "Retrieves POIs pending verification (admin only)")
    public ResponseEntity<Page<PoiResponse>> getUnverifiedPois(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(poiService.getUnverifiedPois(pageable));
    }

    @GetMapping("/city/{cityId}/count")
    @Operation(summary = "Get POI count by city", description = "Returns the number of POIs in a city")
    public ResponseEntity<Long> getPoiCountByCity(@PathVariable Long cityId) {
        return ResponseEntity.ok(poiService.getPoiCountByCity(cityId));
    }
}