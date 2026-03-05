package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.security.SecurityUtils;
import com.travelapp.poi.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
@Tag(name = "Import Management", description = "Endpoints for managing data import tasks")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/start")
    @Operation(summary = "Start import task", description = "Starts a new data import task (admin only)")
    public ResponseEntity<CompletableFuture<ImportTaskResponse>> startImport(
            @Valid @RequestBody ImportTaskRequest request
    ) {
        Long userId = SecurityUtils.requireUserId();
        CompletableFuture<ImportTaskResponse> future = importService.startImport(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(future);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get import task", description = "Retrieves import task details")
    public ResponseEntity<ImportTaskResponse> getImportTask(@PathVariable Long taskId) {
        return ResponseEntity.ok(importService.getImportTask(taskId));
    }

    @GetMapping("/tasks")
    @Operation(summary = "List import tasks", description = "Retrieves a list of import tasks")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(importService.getImportTasks(pageable));
    }

    @GetMapping("/tasks/source/{sourceCode}")
    @Operation(summary = "Get import tasks by source", description = "Retrieves import tasks for a specific source")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasksBySource(
            @PathVariable String sourceCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(importService.getImportTasksBySource(sourceCode, pageable));
    }

    @GetMapping("/tasks/city/{cityId}")
    @Operation(summary = "Get import tasks by city", description = "Retrieves import tasks for a specific city")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasksByCity(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(importService.getImportTasksByCity(cityId, pageable));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "Cancel import task", description = "Cancels a running import task (admin only)")
    public ResponseEntity<Void> cancelImportTask(@PathVariable Long taskId) {
        Long userId = SecurityUtils.requireUserId();
        importService.cancelImportTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "Retry import task", description = "Retries a failed import task (admin only)")
    public ResponseEntity<Void> retryImportTask(@PathVariable Long taskId) {
        Long userId = SecurityUtils.requireUserId();
        importService.retryImportTask(taskId, userId);
        return ResponseEntity.ok().build();
    }
}