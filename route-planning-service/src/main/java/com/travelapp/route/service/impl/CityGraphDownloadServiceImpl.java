package com.travelapp.route.service.impl;

import com.travelapp.route.client.GraphImportClient;
import com.travelapp.route.model.dto.internal.GraphImportTriggerRequest;
import com.travelapp.route.model.dto.response.CityGraphStatusResponse;
import com.travelapp.route.model.entity.CityGraphVersion;
import com.travelapp.route.repository.CityGraphVersionRepository;
import com.travelapp.route.service.CityGraphDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CityGraphDownloadServiceImpl implements CityGraphDownloadService {

    private final GraphImportClient graphImportClient;
    private final CityGraphVersionRepository cityGraphVersionRepository;

    private final Set<Long> importInFlightCities = ConcurrentHashMap.newKeySet();

    @Override
    public CityGraphStatusResponse getStatus(Long cityId) {
        Optional<CityGraphVersion> activeVersion = cityGraphVersionRepository
                .findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE);

        if (activeVersion.isPresent()) {
            importInFlightCities.remove(cityId);
            CityGraphVersion version = activeVersion.get();
            return response(cityId, true, false, true, "READY", 100,
                    valueOrDefault(version.getProgressMessage(), "Граф дорог установлен и готов к построению маршрутов"));
        }

        Optional<CityGraphVersion> latestVersion = cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(cityId);
        if (latestVersion.isPresent()) {
            CityGraphVersion version = latestVersion.get();
            CityGraphVersion.Status status = version.getStatus();
            if (status == CityGraphVersion.Status.DRAFT) {
                return response(cityId, false, true, false, "DOWNLOADING",
                        version.getProgressPercent(),
                        valueOrDefault(version.getProgressMessage(), "Скачиваем и подготавливаем граф дорог"));
            }
            if (status == CityGraphVersion.Status.FAILED) {
                importInFlightCities.remove(cityId);
                return response(cityId, false, false, false, "FAILED",
                        version.getProgressPercent(),
                        valueOrDefault(version.getProgressMessage(), "Не удалось загрузить граф дорог. Попробуйте повторить позже"));
            }
        }

        if (importInFlightCities.contains(cityId)) {
            return response(cityId, false, true, false, "DOWNLOADING", 5,
                    "Запрос на загрузку графа дорог отправлен");
        }

        return response(cityId, false, false, false, "NOT_DOWNLOADED", 0,
                "Граф дорог для города пока не установлен");
    }

    @Override
    public CityGraphStatusResponse download(Long cityId) {
        CityGraphStatusResponse current = getStatus(cityId);
        if (current.ready() || current.downloading()) {
            return current;
        }

        importInFlightCities.add(cityId);
        try {
            log.info("Triggering graph import for cityId={}", cityId);
            graphImportClient.importCityGraph(new GraphImportTriggerRequest(cityId, null));
            return response(cityId, false, true, false, "DOWNLOADING", 5,
                    "Запрос на загрузку графа дорог отправлен");
        } catch (Exception ex) {
            importInFlightCities.remove(cityId);
            log.error("Failed to trigger graph import for cityId={}", cityId, ex);
            throw ex;
        }
    }

    private CityGraphStatusResponse response(
            Long cityId,
            boolean downloaded,
            boolean downloading,
            boolean ready,
            String status,
            Integer progressPercent,
            String message
    ) {
        int normalizedProgress = normalizeProgress(progressPercent, status);
        return new CityGraphStatusResponse(cityId, downloaded, downloading, ready, status, normalizedProgress, message);
    }

    private int normalizeProgress(Integer progressPercent, String status) {
        if ("READY".equals(status)) {
            return 100;
        }
        if (progressPercent == null) {
            return "DOWNLOADING".equals(status) ? 5 : 0;
        }
        return Math.max(0, Math.min(100, progressPercent));
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
