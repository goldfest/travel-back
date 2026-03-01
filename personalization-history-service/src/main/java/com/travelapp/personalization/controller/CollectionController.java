package com.travelapp.personalization.controller;

import com.travelapp.personalization.model.dto.request.CollectionPoiRequest;
import com.travelapp.personalization.model.dto.request.CollectionRequest;
import com.travelapp.personalization.model.dto.response.CollectionResponse;
import com.travelapp.personalization.service.CollectionService;
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
@RequestMapping("/personalization/v1/collections")
@RequiredArgsConstructor
@Tag(name = "Collection Management", description = "APIs for managing user collections")
public class CollectionController {

    private final CollectionService collectionService;

    @PostMapping
    @Operation(summary = "Create a new collection")
    public ResponseEntity<CollectionResponse> createCollection(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CollectionRequest request) {

        CollectionResponse response = collectionService.createCollection(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{collectionId}")
    @Operation(summary = "Update collection")
    public ResponseEntity<CollectionResponse> updateCollection(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionRequest request) {

        CollectionResponse response = collectionService.updateCollection(userId, collectionId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{collectionId}")
    @Operation(summary = "Delete collection")
    public ResponseEntity<Void> deleteCollection(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId) {

        collectionService.deleteCollection(userId, collectionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{collectionId}")
    @Operation(summary = "Get collection by ID")
    public ResponseEntity<CollectionResponse> getCollection(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId) {

        CollectionResponse response = collectionService.getCollection(userId, collectionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get user collections")
    public ResponseEntity<Page<CollectionResponse>> getUserCollections(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<CollectionResponse> collections = collectionService.getUserCollections(userId, pageable);
        return ResponseEntity.ok(collections);
    }

    @GetMapping("/search")
    @Operation(summary = "Search collections by name")
    public ResponseEntity<List<CollectionResponse>> searchCollections(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String query) {

        List<CollectionResponse> collections = collectionService.searchCollections(userId, query);
        return ResponseEntity.ok(collections);
    }

    @PostMapping("/{collectionId}/pois")
    @Operation(summary = "Add POI to collection")
    public ResponseEntity<Void> addPoiToCollection(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId,
            @Valid @RequestBody CollectionPoiRequest request) {

        collectionService.addPoiToCollection(userId, collectionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{collectionId}/pois/{poiId}")
    @Operation(summary = "Remove POI from collection")
    public ResponseEntity<Void> removePoiFromCollection(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId,
            @PathVariable Long poiId) {

        collectionService.removePoiFromCollection(userId, collectionId, poiId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{collectionId}/pois")
    public ResponseEntity<Page<Long>> getCollectionPois(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<Long> poiIds = collectionService.getCollectionPois(userId, collectionId, pageable);
        return ResponseEntity.ok(poiIds);
    }

    @GetMapping("/{collectionId}/pois/count")
    public ResponseEntity<Long> getCollectionPoiCount(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long collectionId) {

        Long count = collectionService.getCollectionPoiCount(userId, collectionId);
        return ResponseEntity.ok(count);
    }
}