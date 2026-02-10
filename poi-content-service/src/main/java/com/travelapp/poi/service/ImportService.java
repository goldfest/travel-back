package com.travelapp.poi.service;

import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.concurrent.CompletableFuture;

public interface ImportService {

    CompletableFuture<ImportTaskResponse> startImport(ImportTaskRequest request, Long userId);

    ImportTaskResponse getImportTask(Long taskId);

    Page<ImportTaskResponse> getImportTasks(Pageable pageable);

    Page<ImportTaskResponse> getImportTasksBySource(String sourceCode, Pageable pageable);

    Page<ImportTaskResponse> getImportTasksByCity(Long cityId, Pageable pageable);

    void cancelImportTask(Long taskId, Long userId);

    void retryImportTask(Long taskId, Long userId);

    void cleanupStalledTasks();
}