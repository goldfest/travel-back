package com.travelapp.personalization.controller;

import com.travelapp.personalization.model.dto.request.SearchHistoryRequest;
import com.travelapp.personalization.model.dto.response.SearchHistoryResponse;
import com.travelapp.personalization.service.SearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/personalization/v1/search-history")
@RequiredArgsConstructor
@Tag(name = "Search History Management", description = "APIs for managing search history")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @PostMapping
    @Operation(summary = "Record a search")
    public ResponseEntity<Void> recordSearch(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SearchHistoryRequest request) {

        searchHistoryService.recordSearch(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping
    @Operation(summary = "Get user search history")
    public ResponseEntity<Page<SearchHistoryResponse>> getUserSearchHistory(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<SearchHistoryResponse> history = searchHistoryService.getUserSearchHistory(userId, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/city/{cityId}")
    @Operation(summary = "Get user search history for specific city")
    public ResponseEntity<Page<SearchHistoryResponse>> getUserCitySearchHistory(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long cityId,
            @Parameter(description = "Pagination parameters") Pageable pageable) {

        Page<SearchHistoryResponse> history = searchHistoryService.getUserCitySearchHistory(
                userId, cityId, pageable);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/recent-queries")
    @Operation(summary = "Get recent search queries")
    public ResponseEntity<List<String>> getRecentQueries(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "10") int limit) {

        List<String> queries = searchHistoryService.getRecentQueries(userId, limit);
        return ResponseEntity.ok(queries);
    }

    @DeleteMapping
    @Operation(summary = "Clear user search history")
    public ResponseEntity<Void> clearUserHistory(
            @RequestHeader("X-User-Id") Long userId) {

        searchHistoryService.clearUserHistory(userId);
        return ResponseEntity.noContent().build();
    }
}