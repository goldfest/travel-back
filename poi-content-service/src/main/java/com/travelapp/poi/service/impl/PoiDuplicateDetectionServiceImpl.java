package com.travelapp.poi.service.impl;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiSourceRepository;
import com.travelapp.poi.service.PoiDuplicateDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiDuplicateDetectionServiceImpl implements PoiDuplicateDetectionService {

    private final PoiRepository poiRepository;
    private final PoiSourceRepository poiSourceRepository;

    @Override
    public Optional<Poi> findDuplicate(PoiCreateRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Optional<Poi> bySource = findBySource(request);
        if (bySource.isPresent()) {
            log.info("Duplicate detected by source for POI name={}", request.getName());
            return bySource;
        }

        Optional<Poi> byNameAndAddress = findByNameAndAddress(request);
        if (byNameAndAddress.isPresent()) {
            log.info("Duplicate detected by name+address for POI name={}", request.getName());
            return byNameAndAddress;
        }

        return Optional.empty();
    }

    private Optional<Poi> findBySource(PoiCreateRequest request) {
        if (request.getSources() == null || request.getSources().isEmpty()) {
            return Optional.empty();
        }

        for (PoiCreateRequest.SourceRequest source : request.getSources()) {
            if (source.getSourceCode() == null || source.getSourceUrl() == null
                    || source.getSourceCode().isBlank() || source.getSourceUrl().isBlank()) {
                continue;
            }

            var poiSource = poiSourceRepository.findFirstBySourceCodeAndSourceUrl(
                    source.getSourceCode(),
                    source.getSourceUrl()
            );

            if (poiSource.isPresent() && poiSource.get().getPoi() != null) {
                return Optional.of(poiSource.get().getPoi());
            }
        }

        return Optional.empty();
    }

    private Optional<Poi> findByNameAndAddress(PoiCreateRequest request) {
        if (request.getName() == null || request.getAddress() == null || request.getCityId() == null
                || request.getName().isBlank() || request.getAddress().isBlank()) {
            return Optional.empty();
        }

        return poiRepository.findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(
                request.getName().trim(),
                request.getAddress().trim(),
                request.getCityId()
        );
    }
}
