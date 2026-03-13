package com.travelapp.personalization.controller;

import com.travelapp.personalization.model.dto.request.FavoriteRequest;
import com.travelapp.personalization.model.dto.response.FavoriteResponse;
import com.travelapp.personalization.security.SecurityUtils;
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
@RequestMapping("/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorite Management", description = "APIs for managing user favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    @Operation(summary = "Add POI to favorites")
    public ResponseEntity<FavoriteResponse> addToFavorites(
            @Valid @RequestBody FavoriteRequest request) {
        Long userId = SecurityUtils.requireUserId();

        FavoriteResponse response = favoriteService.addToFavorites(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{poiId}")
    @Operation(summary = "Remove POI from favorites")
    public ResponseEntity<Void> removeFromFavorites(
            @PathVariable Long poiId) {
        Long userId = SecurityUtils.requireUserId();

        favoriteService.removeFromFavorites(userId, poiId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get user favorites")
    public ResponseEntity<Page<FavoriteResponse>> getUserFavorites(
            @Parameter(description = "Pagination parameters") Pageable pageable) {
        Long userId = SecurityUtils.requireUserId();

        Page<FavoriteResponse> favorites = favoriteService.getUserFavorites(userId, pageable);
        return ResponseEntity.ok(favorites);
    }

    @GetMapping("/check/{poiId}")
    @Operation(summary = "Check if POI is in favorites")
    public ResponseEntity<Boolean> checkFavorite(
            @PathVariable Long poiId) {
        Long userId = SecurityUtils.requireUserId();

        boolean isFavorite = favoriteService.isFavorite(userId, poiId);
        return ResponseEntity.ok(isFavorite);
    }

    @GetMapping("/count")
    @Operation(summary = "Get favorite count")
    public ResponseEntity<Long> getFavoriteCount() {
        Long userId = SecurityUtils.requireUserId();

        Long count = favoriteService.getFavoriteCount(userId);
        return ResponseEntity.ok(count);
    }

    @DeleteMapping
    @Operation(summary = "Delete all user favorites")
    public ResponseEntity<Void> deleteAllFavorites() {
        Long userId = SecurityUtils.requireUserId();

        favoriteService.deleteAllUserFavorites(userId);
        return ResponseEntity.noContent().build();
    }
}