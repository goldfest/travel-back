package com.travelapp.poi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelapp.poi.client.MlPoiWorkerClient;
import com.travelapp.poi.mapper.ImportTaskMapper;
import com.travelapp.poi.mapper.MlPoiMapper;
import com.travelapp.poi.mapper.MlRawRequestMapper;
import com.travelapp.poi.model.dto.request.ImportTaskRequest;
import com.travelapp.poi.model.dto.response.ImportTaskResponse;
import com.travelapp.poi.model.entity.DataImportTask;
import com.travelapp.poi.model.ml.MlEnrichResponse;
import com.travelapp.poi.model.ml.MlStatusRecommendation;
import com.travelapp.poi.model.ml.request.MlEnrichRawRequest;
import com.travelapp.poi.model.ml.request.MlRawMediaDto;
import com.travelapp.poi.repository.DataImportTaskRepository;
import com.travelapp.poi.service.ImportService;
import com.travelapp.poi.service.slug.SlugService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    private final MlPoiWorkerClient mlPoiWorkerClient;
    private final MlPoiMapper mlPoiMapper;
    private final MlRawRequestMapper mlRawRequestMapper;

    private final SlugService slugService;

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
    public ImportTaskResponse startImport(ImportTaskRequest request, Long userId) {
        log.info("Starting import task for source: {}, query: {}", request.getSourceCode(), request.getQuery());

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
            task.setStartedAt(null);
            task.setFinishedAt(null);
            importTaskRepository.save(task);

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
            log.warn("Marked stalled task as failed: {}", task.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void executeImport(Long taskId, Long userId) {
        DataImportTask task = importTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Import task not found: " + taskId));

        log.info("Executing import task: {}", task.getId());

        task.start();
        importTaskRepository.save(task);

        try {
            String source = task.getSourceCode() == null ? "" : task.getSourceCode().trim().toLowerCase();

            switch (source) {
                case "google_maps":
                    importFromGoogleMaps(task, userId);
                    break;
                case "yandex":
                    importFromYandex(task, userId);
                    break;
                case "wiki":
                case "wikipedia":
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

            DataImportTask freshTask = importTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Import task not found before completion: " + taskId));

            if (freshTask.getStatus() == DataImportTask.ImportStatus.FAILED
                    && freshTask.getErrorMessage() != null
                    && freshTask.getErrorMessage().startsWith("Cancelled by user")) {
                log.info("Import task {} was cancelled, skipping success completion", taskId);
                return;
            }

            freshTask.setTotalPoiFound(task.getTotalPoiFound());
            freshTask.setTotalPoiCreated(task.getTotalPoiCreated());
            freshTask.setTotalPoiUpdated(task.getTotalPoiUpdated());
            freshTask.complete(task.getTotalPoiCreated(), task.getTotalPoiUpdated());
            importTaskRepository.save(freshTask);

            log.info("Import task completed successfully: {}", freshTask.getId());

        } catch (Exception e) {
            log.error("Import task failed: {}", e.getMessage(), e);

            DataImportTask failedTask = importTaskRepository.findById(taskId)
                    .orElseThrow(() -> new RuntimeException("Import task not found on failure: " + taskId));

            if (failedTask.getStatus() != DataImportTask.ImportStatus.FAILED) {
                failedTask.fail(e.getMessage());
                importTaskRepository.save(failedTask);
            }

            throw new RuntimeException("Import execution failed", e);
        }
    }

    private void importFromGoogleMaps(DataImportTask task, Long userId) {
        log.info("Importing from Google Maps: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for Google Maps import");
            }

            MlEnrichRawRequest enrichRequest = mlRawRequestMapper.buildRequest(
                    task.getCityId(),
                    "ru",
                    "landmark",
                    "GOOGLE_MAPS",
                    task.getQuery(),
                    null,
                    "Тестовый объект Google Maps",
                    "Тестовое описание объекта, полученного из Google Maps. Оно используется как временный raw payload для проверки интеграции Java и ML.",
                    "Неизвестный адрес",
                    55.751244,
                    37.618423,
                    null,
                    task.getQuery(),
                    0,
                    "landmark",
                    Map.of("touristAttraction", "true"),
                    List.of(),
                    List.of()
            );

            found++;
            if (processMlEnrichmentAndCreatePoi(task, userId, enrichRequest)) {
                created++;
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("Google Maps import failed: " + ex.getMessage(), ex);
        }
    }

    private void importFromYandex(DataImportTask task, Long userId) {
        log.info("Importing from Yandex: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for Yandex import");
            }

            MlEnrichRawRequest enrichRequest = mlRawRequestMapper.buildRequest(
                    task.getCityId(),
                    "ru",
                    "landmark",
                    "YANDEX",
                    task.getQuery(),
                    null,
                    "Тестовый объект Yandex",
                    "Тестовое описание объекта, полученного из Yandex. Используется для проверки общей интеграции импорта с ML сервисом.",
                    "Неизвестный адрес",
                    55.751244,
                    37.618423,
                    null,
                    task.getQuery(),
                    0,
                    "landmark",
                    Map.of("touristAttraction", "true"),
                    List.of(),
                    List.of()
            );

            found++;
            if (processMlEnrichmentAndCreatePoi(task, userId, enrichRequest)) {
                created++;
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("Yandex import failed: " + ex.getMessage(), ex);
        }
    }

    private void importFromWikipedia(DataImportTask task, Long userId) {
        log.info("Importing from Wikipedia: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for Wikipedia import");
            }

            var request = new com.travelapp.poi.model.ml.request.MlImportFromSourceRequest();
            request.setSourceCode("WIKIPEDIA");
            request.setSourceUrl(task.getQuery());
            request.setCityId(task.getCityId());
            request.setLanguage("ru");
            request.setPoiTypeHint("landmark");

            MlEnrichResponse enrichResponse = mlPoiWorkerClient.importFromSource(request);
            found++;

            if (enrichResponse == null || enrichResponse.getPoiDraft() == null || enrichResponse.getStatusRecommendation() == null) {
                throw new RuntimeException("Invalid ML enrich response from Wikipedia import");
            }

            if (MlStatusRecommendation.REJECTED.equals(enrichResponse.getStatusRecommendation())) {
                log.warn("Wikipedia POI rejected by ML. taskId={}, errors={}, warnings={}",
                        task.getId(),
                        enrichResponse.getQuality() != null ? enrichResponse.getQuality().getErrors() : null,
                        enrichResponse.getQuality() != null ? enrichResponse.getQuality().getWarnings() : null);
            } else {
                var createRequest = mlPoiMapper.toPoiCreateRequest(enrichResponse);

                // уникализируем slug перед созданием
                createRequest.setSlug(slugService.makeUniqueSlug(createRequest.getSlug()));

                var createdPoi = poiService.createPoi(createRequest, userId);
                created++;

                if (MlStatusRecommendation.AUTO_PUBLISH.equals(enrichResponse.getStatusRecommendation())) {
                    poiService.verifyPoiInternal(createdPoi.getId());
                }

                log.info("Wikipedia POI imported successfully. taskId={}, poiId={}, statusRecommendation={}",
                        task.getId(),
                        createdPoi.getId(),
                        enrichResponse.getStatusRecommendation());
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("Wikipedia import failed: " + ex.getMessage(), ex);
        }
    }

    private void importFrom2GIS(DataImportTask task, Long userId) {
        log.info("Importing from 2GIS: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for 2GIS import");
            }

            MlRawMediaDto media = new MlRawMediaDto();
            media.setUrl("https://example.com/media/pushkin-1.jpg");
            media.setMediaType("IMAGE");

            MlEnrichRawRequest enrichRequest = mlRawRequestMapper.buildRequest(
                    task.getCityId(),
                    "ru",
                    "restaurant",
                    "TWO_GIS",
                    task.getQuery(),
                    null,
                    "Ресторан Пушкин",
                    "Известный ресторан русской кухни в центре города. Популярен среди туристов благодаря интерьеру и высокому уровню сервиса.",
                    "Москва, Тверской бульвар, 26А",
                    55.76495,
                    37.60442,
                    "+7-495-000-00-01",
                    task.getQuery(),
                    4,
                    "restaurant",
                    Map.of(
                            "parking", "false",
                            "wifi", "true"
                    ),
                    List.of(),
                    List.of(media)
            );

            found++;
            if (processMlEnrichmentAndCreatePoi(task, userId, enrichRequest)) {
                created++;
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("2GIS import failed: " + ex.getMessage(), ex);
        }
    }

    private void importFromBooking(DataImportTask task, Long userId) {
        log.info("Importing from Booking.com: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for Booking import");
            }

            MlEnrichRawRequest enrichRequest = mlRawRequestMapper.buildRequest(
                    task.getCityId(),
                    "ru",
                    "hotel",
                    "BOOKING",
                    task.getQuery(),
                    null,
                    "Тестовый объект Booking",
                    "Описание объекта размещения, полученного из Booking. Используется как временный payload до подключения реального источника.",
                    "Неизвестный адрес",
                    55.751244,
                    37.618423,
                    null,
                    task.getQuery(),
                    3,
                    "hotel",
                    Map.of("wifi", "true"),
                    List.of(),
                    List.of()
            );

            found++;
            if (processMlEnrichmentAndCreatePoi(task, userId, enrichRequest)) {
                created++;
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("Booking import failed: " + ex.getMessage(), ex);
        }
    }

    private void importManualData(DataImportTask task, Long userId) {
        log.info("Importing manual data: {}", task.getQuery());

        int found = 0;
        int created = 0;
        int updated = 0;

        try {
            if (task.getCityId() == null) {
                throw new IllegalArgumentException("City ID is required for manual import");
            }

            MlEnrichRawRequest enrichRequest = mlRawRequestMapper.buildRequest(
                    task.getCityId(),
                    "ru",
                    "landmark",
                    "MANUAL",
                    task.getQuery(),
                    null,
                    "Ручной объект",
                    task.getQuery(),
                    "Адрес не указан",
                    55.751244,
                    37.618423,
                    null,
                    null,
                    0,
                    "landmark",
                    Map.of(),
                    List.of(),
                    List.of()
            );

            found++;
            if (processMlEnrichmentAndCreatePoi(task, userId, enrichRequest)) {
                created++;
            }

            task.setTotalPoiFound(found);
            task.setTotalPoiCreated(created);
            task.setTotalPoiUpdated(updated);

        } catch (Exception ex) {
            throw new RuntimeException("Manual import failed: " + ex.getMessage(), ex);
        }
    }

    private boolean processMlEnrichmentAndCreatePoi(
            DataImportTask task,
            Long userId,
            MlEnrichRawRequest enrichRequest
    ) {
        MlEnrichResponse enrichResponse = mlPoiWorkerClient.enrichRaw(enrichRequest);

        if (enrichResponse == null || enrichResponse.getPoiDraft() == null || enrichResponse.getStatusRecommendation() == null) {
            throw new RuntimeException("Invalid ML enrich response");
        }

        if (MlStatusRecommendation.REJECTED.equals(enrichResponse.getStatusRecommendation())) {
            log.warn("POI rejected by ML. taskId={}, errors={}, warnings={}",
                    task.getId(),
                    enrichResponse.getQuality() != null ? enrichResponse.getQuality().getErrors() : null,
                    enrichResponse.getQuality() != null ? enrichResponse.getQuality().getWarnings() : null);
            return false;
        }

        var createRequest = mlPoiMapper.toPoiCreateRequest(enrichResponse);
        createRequest.setSlug(slugService.makeUniqueSlug(createRequest.getSlug()));
        var createdPoi = poiService.createPoi(createRequest, userId);

        if (MlStatusRecommendation.AUTO_PUBLISH.equals(enrichResponse.getStatusRecommendation())) {
            poiService.verifyPoiInternal(createdPoi.getId());
        }

        log.info("POI imported successfully. taskId={}, poiId={}, statusRecommendation={}",
                task.getId(),
                createdPoi.getId(),
                enrichResponse.getStatusRecommendation());

        return true;
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