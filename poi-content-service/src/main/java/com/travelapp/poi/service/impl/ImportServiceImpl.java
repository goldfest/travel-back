package com.travelapp.poi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.model.entity.DataImportTask;
import com.travelapp.poi.repository.DataImportTaskRepository;
import com.travelapp.poi.service.ImportService;
import com.travelapp.poi.service.mapper.ImportTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportServiceImpl implements ImportService {

    private final DataImportTaskRepository importTaskRepository;
    private final ImportTaskMapper importTaskMapper;
    private final PoiServiceImpl poiService;
    private final ObjectMapper objectMapper;

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(5);

    @Value("${import.batch.size:50}")
    private int batchSize;

    @Value("${import.batch.retry-attempts:3}")
    private int retryAttempts;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    @Override
    @Transactional
    public CompletableFuture<ImportTaskResponse> startImport(ImportTaskRequest request, Long userId) {
        log.info("Starting import task for source: {}, query: {}", request.getSourceCode(), request.getQuery());

        // Create import task
        DataImportTask task = new DataImportTask();
        task.setSourceCode(request.getSourceCode());
        task.setQuery(request.getQuery());
        task.setCityId(request.getCityId());
        task.setStatus(DataImportTask.ImportStatus.PENDING);
        task = importTaskRepository.save(task);

        // Start import asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                executeImport(task, userId);
                return importTaskMapper.toResponse(task);
            } catch (Exception e) {
                log.error("Import task failed: {}", e.getMessage(), e);
                task.fail(e.getMessage());
                importTaskRepository.save(task);
                throw new RuntimeException("Import failed: " + e.getMessage(), e);
            }
        }, importExecutor);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportTaskResponse getImportTask(Long taskId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        return importTaskMapper.toResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportTaskResponse> getImportTasks(Pageable pageable) {
        Page<DataImportTask> tasks = importTaskRepository.findAll(pageable);
        return tasks.map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportTaskResponse> getImportTasksBySource(String sourceCode, Pageable pageable) {
        Page<DataImportTask> tasks = importTaskRepository.findBySourceCode(sourceCode, pageable);
        return tasks.map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportTaskResponse> getImportTasksByCity(Long cityId, Pageable pageable) {
        Page<DataImportTask> tasks = importTaskRepository.findByCityId(cityId, pageable);
        return tasks.map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional
    public void cancelImportTask(Long taskId, Long userId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        if (task.getStatus() == DataImportTask.ImportStatus.RUNNING) {
            task.fail("Cancelled by user: " + userId);
            importTaskRepository.save(task);
            log.info("Import task cancelled: {}", taskId);
        }
    }

    @Override
    @Transactional
    public void retryImportTask(Long taskId, Long userId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        if (task.getStatus() == DataImportTask.ImportStatus.FAILED) {
            task.setStatus(DataImportTask.ImportStatus.PENDING);
            task.setErrorMessage(null);
            importTaskRepository.save(task);

            // Restart import asynchronously
            CompletableFuture.runAsync(() -> executeImport(task, userId), importExecutor);
        }
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void cleanupStalledTasks() {
        log.info("Checking for stalled import tasks");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        var stalledTasks = importTaskRepository.findStalledTasks(cutoffTime);

        for (DataImportTask task : stalledTasks) {
            task.fail("Task stalled for more than 30 minutes");
            importTaskRepository.save(task);
            log.warn("Marked stalled task as failed: {}", task.getId());
        }
    }

    private void executeImport(DataImportTask task, Long userId) {
        log.info("Executing import task: {}", task.getId());

        task.start();
        importTaskRepository.save(task);

        try {
            // Based on source code, use different import strategies
            switch (task.getSourceCode()) {
                case "google_maps":
                    importFromGoogleMaps(task, userId);
                    break;
                case "yandex":
                    importFromYandex(task, userId);
                    break;
                case "wiki":
                    importFromWikipedia(task, userId);
                    break;
                case "2gis":
                    importFrom2GIS(task, userId);
                    break;
                case "booking":
                    importFromBooking(task, userId);
                    break;
                case "manual_admin":
                    importManualData(task, userId);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported source: " + task.getSourceCode());
            }

            task.complete(task.getTotalPoiCreated(), task.getTotalPoiUpdated());
            importTaskRepository.save(task);

            log.info("Import task completed successfully: {}", task.getId());

        } catch (Exception e) {
            log.error("Import task failed: {}", e.getMessage(), e);
            task.fail(e.getMessage());
            importTaskRepository.save(task);
            throw new RuntimeException("Import execution failed", e);
        }
    }

    private void importFromGoogleMaps(DataImportTask task, Long userId) {
        log.info("Importing from Google Maps: {}", task.getQuery());

        // This is a placeholder implementation
        // In real implementation, you would call Google Places API
        try {
            // Simulate API call
            Thread.sleep(2000);

            // Parse response and create POIs
            int created = 0;
            int updated = 0;

            // For demonstration, create some sample POIs
            if (task.getCityId() != null) {
                // Create sample museums
                created += createSamplePois(task.getCityId(), userId, "museum");
                // Create sample restaurants
                created += createSamplePois(task.getCityId(), userId, "restaurant");
            }

            task.setTotalPoiFound(created + updated);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Import interrupted", e);
        }
    }

    private void importFromYandex(DataImportTask task, Long userId) {
        log.info("Importing from Yandex: {}", task.getQuery());
        // Similar to Google Maps implementation
    }

    private void importFromWikipedia(DataImportTask task, Long userId) {
        log.info("Importing from Wikipedia: {}", task.getQuery());
        // Implementation for Wikipedia import
    }

    private void importFrom2GIS(DataImportTask task, Long userId) {
        log.info("Importing from 2GIS: {}", task.getQuery());
        // Implementation for 2GIS import
    }

    private void importFromBooking(DataImportTask task, Long userId) {
        log.info("Importing from Booking.com: {}", task.getQuery());
        // Implementation for Booking.com import
    }

    private void importManualData(DataImportTask task, Long userId) {
        log.info("Importing manual data: {}", task.getQuery());
        // Implementation for manual data import (CSV, JSON, etc.)
    }

    private int createSamplePois(Long cityId, Long userId, String poiType) {
        // This is a sample implementation
        // In real application, you would parse real data from API responses

        log.debug("Creating sample POIs for city: {}, type: {}", cityId, poiType);
        return 3; // Return number of created POIs
    }

    private Mono<JsonNode> fetchDataFromApi(String apiUrl, String apiKey) {
        return webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        Mono.error(new RuntimeException("API call failed: " + response.statusCode())))
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(30));
    }
}