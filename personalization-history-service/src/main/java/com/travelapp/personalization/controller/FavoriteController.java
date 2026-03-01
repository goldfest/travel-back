package com.travelapp.personalization.controller;

import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.response.FavoriteResponse;
import com.travelapp.personalization.service.FavoriteService;
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

@RestController
@RequestMapping("/personalization/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorite Management", description = "APIs for managing user favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    @Operation(summary = "Add POI to favorites")
    public ResponseEntity<FavoriteResponse> addToFavorites(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody FavoriteRequest request) {

        FavoriteResponse response = favoriteService.addToFavorites(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{poiId}")
    @Operation(summary = "Remove POI from favorites")
    public ResponseEntity<Void> removeFromFavorites(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long poiId) {

        favoriteService.removeFromFavorites(userId, poiId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get user favorites")
    public ResponseEntity<Page<FavoriteResponse>> getUserFavorites(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<FavoriteResponse> favorites = favoriteService.getUserFavorites(userId, pageable);
        return ResponseEntity.ok(favorites);
    }

    @GetMapping("/check/{poiId}")
    @Operation(summary = "Check if POI is in favorites")
    public ResponseEntity<Boolean> checkFavorite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long poiId) {

        boolean isFavorite = favoriteService.isFavorite(userId, poiId);
        return ResponseEntity.ok(isFavorite);
    }

    @GetMapping("/count")
    @Operation(summary = "Get favorite count")
    public ResponseEntity<Long> getFavoriteCount(
            @RequestHeader("X-User-Id") Long userId) {

        Long count = favoriteService.getFavoriteCount(userId);
        return ResponseEntity.ok(count);
    }

    @DeleteMapping
    @Operation(summary = "Delete all user favorites")
    public ResponseEntity<Void> deleteAllFavorites(
            @RequestHeader("X-User-Id") Long userId) {

        favoriteService.deleteAllUserFavorites(userId);
        return ResponseEntity.noContent().build();
    }
}