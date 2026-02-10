package com.travelapp.poi.controller;

import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/poi/import")
@RequiredArgsConstructor
@Tag(name = "Import Management", description = "Endpoints for managing data import tasks")
public class ImportController {

    private final ImportService importService;

    @PostMapping("/start")
    @Operation(summary = "Start import task", description = "Starts a new data import task")
    public ResponseEntity<CompletableFuture<ImportTaskResponse>> startImport(
            @Valid @RequestBody ImportTaskRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        CompletableFuture<ImportTaskResponse> future = importService.startImport(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(future);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get import task", description = "Retrieves import task details")
    public ResponseEntity<ImportTaskResponse> getImportTask(
            @PathVariable Long taskId) {
        ImportTaskResponse response = importService.getImportTask(taskId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks")
    @Operation(summary = "List import tasks", description = "Retrieves a list of import tasks")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ImportTaskResponse> response = importService.getImportTasks(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/source/{sourceCode}")
    @Operation(summary = "Get import tasks by source", description = "Retrieves import tasks for a specific source")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasksBySource(
            @PathVariable String sourceCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ImportTaskResponse> response = importService.getImportTasksBySource(sourceCode, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/city/{cityId}")
    @Operation(summary = "Get import tasks by city", description = "Retrieves import tasks for a specific city")
    public ResponseEntity<Page<ImportTaskResponse>> getImportTasksByCity(
            @PathVariable Long cityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ImportTaskResponse> response = importService.getImportTasksByCity(cityId, pageable);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @Operation(summary = "Cancel import task", description = "Cancels a running import task")
    public ResponseEntity<Void> cancelImportTask(
            @PathVariable Long taskId,
            @RequestHeader("X-User-Id") Long userId) {
        importService.cancelImportTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks/{taskId}/retry")
    @Operation(summary = "Retry import task", description = "Retries a failed import task")
    public ResponseEntity<Void> retryImportTask(
            @PathVariable Long taskId,
            @RequestHeader("X-User-Id") Long userId) {
        importService.retryImportTask(taskId, userId);
        return ResponseEntity.ok().build();
    }
}