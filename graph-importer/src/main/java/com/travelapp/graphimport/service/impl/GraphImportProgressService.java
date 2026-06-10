package com.travelapp.graphimport.service.impl;

import com.travelapp.graphimport.model.entity.CityGraphVersion;
import com.travelapp.graphimport.repository.CityGraphVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GraphImportProgressService {

    private final CityGraphVersionRepository graphVersionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CityGraphVersion createDraftVersion(
            Long cityId,
            Integer versionNo,
            Double bboxMinLat,
            Double bboxMinLng,
            Double bboxMaxLat,
            Double bboxMaxLng
    ) {
        CityGraphVersion version = new CityGraphVersion();
        version.setCityId(cityId);
        version.setVersionNo(versionNo);
        version.setStatus(CityGraphVersion.Status.DRAFT);
        version.setProgressPercent(0);
        version.setProgressMessage("Граф дорог загружается");
        version.setBboxMinLat(bboxMinLat);
        version.setBboxMinLng(bboxMinLng);
        version.setBboxMaxLat(bboxMaxLat);
        version.setBboxMaxLng(bboxMaxLng);
        return graphVersionRepository.saveAndFlush(version);
    }

    /**
     * Оставлено только для совместимости со старым кодом/эндпоинтами.
     * Частые обновления прогресса во время массового импорта больше не используются.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(Long versionId, int progressPercent, String progressMessage) {
        graphVersionRepository.findById(versionId).ifPresent(version -> {
            version.setProgressPercent(Math.max(0, Math.min(100, progressPercent)));
            version.setProgressMessage(progressMessage);
            graphVersionRepository.saveAndFlush(version);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CityGraphVersion activateVersion(Long versionId) {
        CityGraphVersion version = graphVersionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("Не найдена версия графа дорог: " + versionId));

        graphVersionRepository.archiveActiveByCityId(version.getCityId());
        version.setStatus(CityGraphVersion.Status.ACTIVE);
        version.setImportedAt(LocalDateTime.now());
        version.setFailureReason(null);
        version.setProgressPercent(100);
        version.setProgressMessage("Граф дорог установлен и готов к построению маршрутов");
        return graphVersionRepository.saveAndFlush(version);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long versionId, String failureReason, String progressMessage) {
        graphVersionRepository.findById(versionId).ifPresent(version -> {
            version.setStatus(CityGraphVersion.Status.FAILED);
            version.setFailureReason(failureReason);
            version.setProgressPercent(0);
            version.setProgressMessage(progressMessage);
            graphVersionRepository.saveAndFlush(version);
        });
    }
}
