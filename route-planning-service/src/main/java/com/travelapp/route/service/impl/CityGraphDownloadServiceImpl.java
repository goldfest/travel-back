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
        boolean ready = cityGraphVersionRepository
                .findFirstByCityIdAndStatusOrderByVersionNoDesc(cityId, CityGraphVersion.Status.ACTIVE)
                .isPresent();

        if (ready) {
            importInFlightCities.remove(cityId);
            return new CityGraphStatusResponse(cityId, true, false, true, "READY");
        }

        Optional<CityGraphVersion> latestVersion = cityGraphVersionRepository.findFirstByCityIdOrderByVersionNoDesc(cityId);
        if (latestVersion.isPresent()) {
            CityGraphVersion.Status status = latestVersion.get().getStatus();
            if (status == CityGraphVersion.Status.DRAFT) {
                return new CityGraphStatusResponse(cityId, false, true, false, "DOWNLOADING");
            }
            if (status == CityGraphVersion.Status.FAILED) {
                importInFlightCities.remove(cityId);
                return new CityGraphStatusResponse(cityId, false, false, false, "FAILED");
            }
        }

        if (importInFlightCities.contains(cityId)) {
            return new CityGraphStatusResponse(cityId, false, true, false, "DOWNLOADING");
        }

        return new CityGraphStatusResponse(cityId, false, false, false, "NOT_DOWNLOADED");
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
            return new CityGraphStatusResponse(cityId, false, true, false, "DOWNLOADING");
        } catch (Exception ex) {
            importInFlightCities.remove(cityId);
            log.error("Failed to trigger graph import for cityId={}", cityId, ex);
            throw ex;
        }
    }
}
