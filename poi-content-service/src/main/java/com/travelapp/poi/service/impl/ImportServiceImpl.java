package com.travelapp.poi.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.poi.client.MlPoiWorkerClient;
import com.travelapp.poi.client.TwoGisClient;
import com.travelapp.poi.mapper.ImportTaskMapper;
import com.travelapp.poi.mapper.MlPoiMapper;
import com.travelapp.poi.mapper.MlRawRequestMapper;
import com.travelapp.poi.mapper.TwoGisToMlRawMapper;
import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.model.entity.DataImportTask;
import com.travelapp.poi.model.ml.MlEnrichResponse;
import com.travelapp.poi.model.ml.MlStatusRecommendation;
import com.travelapp.poi.model.ml.request.MlEnrichRawRequest;
import com.travelapp.poi.model.ml.request.MlImportFromSourceRequest;
import com.travelapp.poi.repository.DataImportTaskRepository;
import com.travelapp.poi.service.ImportService;
import com.travelapp.poi.service.PoiDuplicateDetectionService;
import com.travelapp.poi.service.slug.SlugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportServiceImpl implements ImportService {

    private enum PoiImportOutcome {
        CREATED,
        UPDATED,
        REJECTED,
        SKIPPED
    }
    private static final String CANCELLED_PREFIX = "Cancelled by user";

    private final DataImportTaskRepository importTaskRepository;
    private final ImportTaskMapper importTaskMapper;
    private final PoiServiceImpl poiService;
    private final MlPoiWorkerClient mlPoiWorkerClient;
    private final MlPoiMapper mlPoiMapper;
    private final SlugService slugService;

    private final TwoGisClient twoGisClient;
    private final TwoGisToMlRawMapper twoGisToMlRawMapper;

    private final PoiDuplicateDetectionService poiDuplicateDetectionService;

    private final ExecutorService importExecutor = Executors.newFixedThreadPool(5);

    private static final Set<String> BLOCKED_WORDS = Set.of(
            "бляд", "бля", "сука", "хуй", "нахуй", "пизд", "ебан", "ебать", "мразь"
    );

    @Value("${import.progress.log-every:10}")
    private int logEvery;

    @Override
    @Transactional
    public ImportTaskResponse startImport(ImportTaskRequest request, Long userId) {
        log.info("Starting import task for source={}, query='{}', cityId={}, userId={}",
                request.getSourceCode(), request.getQuery(), request.getCityId(), userId);

        DataImportTask task = new DataImportTask();
        task.setSourceCode(request.getSourceCode());
        task.setQuery(request.getQuery());
        task.setCityId(request.getCityId());
        task.setStatus(DataImportTask.ImportStatus.PENDING);

        DataImportTask savedTask = importTaskRepository.save(task);
        Long taskId = savedTask.getId();

        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        CompletableFuture.runAsync(() -> {
                            try {
                                executeImport(taskId, userId);
                            } catch (Exception e) {
                                log.error("Async import failed for task {}: {}", taskId, e.getMessage(), e);
                            }
                        }, importExecutor);
                    }
                }
        );

        return importTaskMapper.toResponse(savedTask);
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
        return importTaskRepository.findAll(pageable).map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportTaskResponse> getImportTasksBySource(String sourceCode, Pageable pageable) {
        return importTaskRepository.findBySourceCode(sourceCode, pageable).map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ImportTaskResponse> getImportTasksByCity(Long cityId, Pageable pageable) {
        return importTaskRepository.findByCityId(cityId, pageable).map(importTaskMapper::toResponse);
    }

    @Override
    @Transactional
    public void cancelImportTask(Long taskId, Long userId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        if (task.getStatus() == DataImportTask.ImportStatus.PENDING
                || task.getStatus() == DataImportTask.ImportStatus.RUNNING) {

            task.fail(CANCELLED_PREFIX + ": " + userId);
            importTaskRepository.save(task);
            log.info("Import task cancelled: taskId={}, userId={}", taskId, userId);
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
            task.setStartedAt(null);
            task.setFinishedAt(null);
            importTaskRepository.save(task);

            log.info("Retrying import task: taskId={}, userId={}", taskId, userId);

            CompletableFuture.runAsync(() -> executeImport(task.getId(), userId), importExecutor);
        }
    }

    @Override
    @Transactional
    @Scheduled(fixedDelay = 300000)
    public void cleanupStalledTasks() {
        log.info("Checking for stalled import tasks");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        var stalledTasks = importTaskRepository.findStalledTasks(cutoffTime);

        for (DataImportTask task : stalledTasks) {
            task.fail("Task stalled for more than 30 minutes");
            importTaskRepository.save(task);
            log.warn("Marked stalled task as failed: taskId={}", task.getId());

        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeImport(Long taskId, Long userId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        log.info("Executing import task: taskId={}, source={}, query='{}', cityId={}, userId={}",
                task.getId(), task.getSourceCode(), task.getQuery(), task.getCityId(), userId);

        task.start();
        importTaskRepository.save(task);

        try {
            String source = task.getSourceCode() == null ? "" : task.getSourceCode().trim().toLowerCase();

            switch (source) {
                case "wiki", "wikipedia" -> importFromWikipedia(task, userId);
                case "2gis" -> importFrom2GIS(task, userId);
                default -> throw new IllegalArgumentException("Unsupported source: " + task.getSourceCode());
            }

            DataImportTask freshTask = importTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Import task not found before completion: " + taskId));

            if (isCancelledTask(freshTask)) {
                log.info("Import task {} was cancelled, skipping success completion", taskId);
                return;
            }

            freshTask.complete(
                    safeInt(task.getTotalPoiCreated()),
                    safeInt(task.getTotalPoiUpdated())
            );
            freshTask.setTotalPoiFound(safeInt(task.getTotalPoiFound()));
            freshTask.setTotalPoiRejected(safeInt(task.getTotalPoiRejected()));
            freshTask.setTotalPoiSkipped(safeInt(task.getTotalPoiSkipped()));
            importTaskRepository.save(freshTask);

            log.info("Import task completed: taskId={}, found={}, created={}, updated={}, rejected={}, skipped={}",
                    freshTask.getId(),
                    freshTask.getTotalPoiFound(),
                    freshTask.getTotalPoiCreated(),
                    freshTask.getTotalPoiUpdated(),
                    freshTask.getTotalPoiRejected(),
                    freshTask.getTotalPoiSkipped());

        } catch (Exception e) {

            log.error("Import task failed: taskId={}, error={}", taskId, e.getMessage(), e);

            DataImportTask failedTask = importTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Import task not found on failure: " + taskId));

            if (failedTask.getStatus() != DataImportTask.ImportStatus.FAILED) {
                failedTask.fail(e.getMessage());
                importTaskRepository.save(failedTask);
            }

            throw new RuntimeException("Import execution failed", e);
        }
    }

    private void importFromWikipedia(DataImportTask task, Long userId) {
        String sourceUrl = normalizeWikipediaSourceUrl(task.getQuery());

        log.info(
                "Importing from Wikipedia: taskId={}, query='{}', sourceUrl='{}'",
                task.getId(),
                task.getQuery(),
                sourceUrl
        );

        int found = 0;
        int created = 0;
        int updated = 0;
        int rejected = 0;
        int skipped = 0;

        try {
            requireCityId(task, "Wikipedia");

            MlImportFromSourceRequest request = new MlImportFromSourceRequest();
            request.setSourceCode("WIKIPEDIA");
            request.setSourceUrl(sourceUrl);
            request.setCityId(task.getCityId());
            request.setLanguage("ru");
            request.setPoiTypeHint("landmark");

            MlEnrichResponse enrichResponse = mlPoiWorkerClient.importFromSource(request);
            found++;

            PoiImportOutcome outcome = processEnrichedPoi(task, userId, enrichResponse, sourceUrl);

            if (outcome == PoiImportOutcome.CREATED) {
                created++;
            } else if (outcome == PoiImportOutcome.UPDATED) {
                updated++;
            } else if (outcome == PoiImportOutcome.REJECTED) {
                rejected++;
            } else {
                skipped++;
            }

            applyCounters(task, found, created, updated, rejected, skipped);
            logTaskSummary("wikipedia", task, found, created, updated, rejected, skipped);

        } catch (Exception ex) {
            throw new RuntimeException("Wikipedia import failed: " + ex.getMessage(), ex);
        }
    }

    private String normalizeWikipediaSourceUrl(String queryOrUrl) {
        if (queryOrUrl == null || queryOrUrl.isBlank()) {
            throw new IllegalArgumentException("Wikipedia query or URL must not be blank");
        }

        String value = queryOrUrl.trim();

        if (isHttpUrl(value)) {
            return value;
        }

        String normalizedTitle = value
                .replace(' ', '_')
                .replaceAll("_+", "_");

        String encodedTitle = java.net.URLEncoder.encode(
                normalizedTitle,
                java.nio.charset.StandardCharsets.UTF_8
        );

        return "https://ru.wikipedia.org/wiki/" + encodedTitle;
    }

    private boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private void importFrom2GIS(DataImportTask task, Long userId) {
        log.info("Importing from 2GIS: taskId={}, query='{}', cityId={}", task.getId(), task.getQuery(), task.getCityId());

        int found = 0;
        int created = 0;
        int updated = 0;
        int rejected = 0;
        int skipped = 0;
        int processed = 0;

        try {
            requireCityId(task, "2GIS");

            var rawPois = twoGisClient.search(task.getQuery(), task.getCityId());
            found = rawPois.size();
            log.info("2GIS raw POIs fetched: taskId={}, totalRaw={}", task.getId(), found);


            for (var rawPoi : rawPois) {
                try {
                    if (isTaskCancelled(task.getId())) {
                        log.info("Stopping 2GIS import for cancelled task {}", task.getId());
                        break;
                    }
                    processed++;
                    log.info("Processing 2GIS POI: taskId={}, index={}, externalId={}, name='{}', sourceUrl={}",
                            task.getId(), processed, rawPoi.getExternalId(), rawPoi.getName(), rawPoi.getSourceUrl());

                    if (rawPoi.getName() == null || rawPoi.getName().isBlank()
                            || rawPoi.getLatitude() == null
                            || rawPoi.getLongitude() == null) {
                        log.warn("Skipping invalid raw 2GIS POI before ML. taskId={}, externalId={}, name={}",
                                task.getId(),
                                rawPoi.getExternalId(),
                                rawPoi.getName());
                        continue;
                    }

                    MlEnrichRawRequest enrichRequest = twoGisToMlRawMapper.toMlRequest(rawPoi, task.getCityId());
                    log.info("Sending raw POI to ML: taskId={}, externalId={}, poiTypeCode={}, mediaCount={}, hoursCount={}",
                            task.getId(),
                            rawPoi.getExternalId(),
                            rawPoi.getPoiTypeCode(),
                            rawPoi.getMedia() != null ? rawPoi.getMedia().size() : 0,
                            rawPoi.getHours() != null ? rawPoi.getHours().size() : 0);
                    MlEnrichResponse enrichResponse = mlPoiWorkerClient.enrichRaw(enrichRequest);

                    if (enrichResponse == null || enrichResponse.getPoiDraft() == null || enrichResponse.getStatusRecommendation() == null) {
                        skipped++;
                        log.warn("Skipping invalid ML response for rawPoi externalId={}", rawPoi.getExternalId());
                        continue;
                    }

                    PoiImportOutcome outcome = processEnrichedPoi(task, userId, enrichResponse, rawPoi.getExternalId());
                    if (outcome == PoiImportOutcome.CREATED) {
                        created++;
                    } else if (outcome == PoiImportOutcome.UPDATED) {
                        updated++;
                    } else if (outcome == PoiImportOutcome.REJECTED) {
                        rejected++;
                    } else {
                        skipped++;
                    }

                    if (processed == 1 || processed % Math.max(logEvery, 1) == 0) {
                        logTaskSummary("2gis-progress", task, found, created, updated, rejected, skipped);
                    }

                } catch (Exception itemEx) {
                    skipped++;
                    log.error("Failed to process one 2GIS POI. taskId={}, externalId={}, rawName={}, error={}",
                            task.getId(),
                            rawPoi.getExternalId(),
                            rawPoi.getName(),
                            itemEx.getMessage(),
                            itemEx);
                }
            }

            applyCounters(task, found, created, updated, rejected, skipped);
            logTaskSummary("2gis", task, found, created, updated, rejected, skipped);

        } catch (Exception ex) {
            throw new RuntimeException("2GIS import failed: " + ex.getMessage(), ex);
        }
    }

    private PoiImportOutcome processEnrichedPoi(
            DataImportTask task,
            Long userId,
            MlEnrichResponse enrichResponse,
            String externalId
    ) {
        if (enrichResponse == null || enrichResponse.getPoiDraft() == null || enrichResponse.getStatusRecommendation() == null) {
            log.warn("Invalid ML enrich response: taskId={}, externalId={}", task.getId(), externalId);
            return PoiImportOutcome.SKIPPED;
        }

        log.info("ML enrich response received: taskId={}, externalId={}, status={}, quality={}, warnings={}",
                task.getId(),
                externalId,
                enrichResponse.getStatusRecommendation(),
                enrichResponse.getQuality() != null ? enrichResponse.getQuality().getQualityScore() : null,
                enrichResponse.getQuality() != null ? enrichResponse.getQuality().getWarnings() : null);

        if (MlStatusRecommendation.REJECTED.equals(enrichResponse.getStatusRecommendation())) {
            log.warn("POI rejected by ML. taskId={}, externalId={}, errors={}, warnings={}",
                    task.getId(),
                    externalId,
                    enrichResponse.getQuality() != null ? enrichResponse.getQuality().getErrors() : null,
                    enrichResponse.getQuality() != null ? enrichResponse.getQuality().getWarnings() : null);
            return PoiImportOutcome.REJECTED;
        }

        var createRequest = mlPoiMapper.toPoiCreateRequest(enrichResponse);
        boolean forceManualReview = shouldForceManualReview(createRequest);

        var duplicate = poiDuplicateDetectionService.findDuplicate(createRequest);

        if (duplicate.isPresent()) {
            var updatedPoi = poiService.updatePoiFromImport(duplicate.get().getId(), createRequest, userId);

            if (MlStatusRecommendation.AUTO_PUBLISH.equals(enrichResponse.getStatusRecommendation()) && !forceManualReview) {
                poiService.verifyPoiInternal(updatedPoi.getId());
            } else {
                log.info("POI kept unverified after import. taskId={}, externalId={}, poiId={}, forceManualReview={}",
                        task.getId(), externalId, updatedPoi.getId(), forceManualReview);
            }

            log.info("POI updated from import. taskId={}, externalId={}, poiId={}, statusRecommendation={}, duplicatePoiId={}",
                    task.getId(),
                    externalId,
                    updatedPoi.getId(),
                    enrichResponse.getStatusRecommendation(),
                    duplicate.get().getId());
            return PoiImportOutcome.UPDATED;
        }

        createRequest.setSlug(slugService.makeUniqueSlug(createRequest.getSlug()));
        var createdPoi = poiService.createPoi(createRequest, userId);

        if (MlStatusRecommendation.AUTO_PUBLISH.equals(enrichResponse.getStatusRecommendation()) && !forceManualReview) {
            poiService.verifyPoiInternal(createdPoi.getId());
        } else {
            log.info("POI created but kept unverified. taskId={}, externalId={}, poiId={}, forceManualReview={}",
                    task.getId(), externalId, createdPoi.getId(), forceManualReview);
        }

        log.info("POI imported successfully. taskId={}, externalId={}, poiId={}, statusRecommendation={}, slug={}",
                task.getId(),
                externalId,
                createdPoi.getId(),
                enrichResponse.getStatusRecommendation(),
                createdPoi.getSlug());
        return PoiImportOutcome.CREATED;
    }

    private boolean isTaskCancelled(Long taskId) {
        return importTaskRepository.findById(taskId)
                .map(this::isCancelledTask)
                .orElse(false);
    }

    private void applyCounters(DataImportTask task, int found, int created, int updated, int rejected, int skipped) {
        task.setTotalPoiFound(found);
        task.setTotalPoiCreated(created);
        task.setTotalPoiUpdated(updated);
        task.setTotalPoiRejected(rejected);
        task.setTotalPoiSkipped(skipped);
    }

    private void logTaskSummary(String stage, DataImportTask task, int found, int created, int updated, int rejected, int skipped) {
        log.info("Import summary [{}]: taskId={}, found={}, created={}, updated={}, rejected={}, skipped={}",
                stage, task.getId(), found, created, updated, rejected, skipped);
    }

    private void requireCityId(DataImportTask task, String sourceName) {
        if (task.getCityId() == null) {
            throw new IllegalArgumentException("City ID is required for " + sourceName + " import");
        }
    }

    private boolean isCancelledTask(DataImportTask task) {
        return task.getStatus() == DataImportTask.ImportStatus.FAILED
                && task.getErrorMessage() != null
                && task.getErrorMessage().startsWith(CANCELLED_PREFIX);
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private boolean containsBlockedWords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = text.toLowerCase()
                .replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        for (String word : BLOCKED_WORDS) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldForceManualReview(PoiCreateRequest request) {
        if (request == null) {
            return true;
        }

        if (containsBlockedWords(request.getName())) {
            return true;
        }

        if (containsBlockedWords(request.getDescription())) {
            return true;
        }

        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            return true;
        }

        String normalized = description.trim();
        if (normalized.length() < 40) {
            return true;
        }

        if ("Описание объекта временно отсутствует.".equalsIgnoreCase(normalized)) {
            return true;
        }

        return false;
    }
}