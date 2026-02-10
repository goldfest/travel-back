package com.travelapp.poi.service.impl;

import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.response.PoiTypeResponse;
import com.travelapp.poi.model.entity.PoiType;
import com.travelapp.poi.repository.PoiTypeRepository;
import com.travelapp.poi.service.PoiTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiTypeServiceImpl implements PoiTypeService {

    private final PoiTypeRepository poiTypeRepository;

    @Override
    @Transactional
    @CacheEvict(value = "poiTypes", allEntries = true)
    public PoiTypeResponse createPoiType(PoiTypeRequest request, Long userId) {
        log.info("Creating new POI type: {} by user {}", request.getCode(), userId);

        // Check if code already exists
        if (poiTypeRepository.existsByCode(request.getCode())) {
            throw new ValidationException("POI Type code already exists: " + request.getCode());
        }

        PoiType poiType = new PoiType();
        poiType.setCode(request.getCode());
        poiType.setName(request.getName());
        poiType.setIcon(request.getIcon());

        poiType = poiTypeRepository.save(poiType);

        return toResponse(poiType);
    }

    @Override
    @Transactional
    @CacheEvict(value = "poiTypes", allEntries = true)
    public PoiTypeResponse updatePoiType(Long id, PoiTypeRequest request, Long userId) {
        log.info("Updating POI type: {} by user {}", id, userId);

        PoiType poiType = poiTypeRepository.findById(id)
                .orElseThrow(() -> new PoiTypeNotFoundException(id));

        // Check if new code already exists (if changed)
        if (!request.getCode().equals(poiType.getCode()) &&
                poiTypeRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new ValidationException("POI Type code already exists: " + request.getCode());
        }

        poiType.setCode(request.getCode());
        poiType.setName(request.getName());
        poiType.setIcon(request.getIcon());

        poiType = poiTypeRepository.save(poiType);

        return toResponse(poiType);
    }

    @Override
    @Transactional
    @CacheEvict(value = "poiTypes", allEntries = true)
    public void deletePoiType(Long id, Long userId) {
        log.info("Deleting POI type: {} by user {}", id, userId);

        PoiType poiType = poiTypeRepository.findById(id)
                .orElseThrow(() -> new PoiTypeNotFoundException(id));

        // Check if POI type is used by any POIs
        // This check should be implemented

        poiTypeRepository.delete(poiType);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiTypes", key = "#id")
    public PoiTypeResponse getPoiTypeById(Long id) {
        log.debug("Fetching POI type by ID: {}", id);

        PoiType poiType = poiTypeRepository.findById(id)
                .orElseThrow(() -> new PoiTypeNotFoundException(id));

        return toResponse(poiType);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiTypes", key = "#code")
    public PoiTypeResponse getPoiTypeByCode(String code) {
        log.debug("Fetching POI type by code: {}", code);

        PoiType poiType = poiTypeRepository.findByCode(code)
                .orElseThrow(() -> new PoiTypeNotFoundException("Code: " + code));

        return toResponse(poiType);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiTypes", key = "'all'")
    public List<PoiTypeResponse> getAllPoiTypes() {
        log.debug("Fetching all POI types");

        return poiTypeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiTypeResponse> getPoiTypes(Pageable pageable) {
        log.debug("Fetching POI types with pagination");

        Page<PoiType> poiTypes = poiTypeRepository.findAll(pageable);
        return poiTypes.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "poiTypes", key = "#codes")
    public List<PoiTypeResponse> getPoiTypesByCodes(List<String> codes) {
        log.debug("Fetching POI types by codes: {}", codes);

        List<PoiType> poiTypes = poiTypeRepository.findByCodeIn(codes);
        return poiTypes.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private PoiTypeResponse toResponse(PoiType poiType) {
        PoiTypeResponse response = new PoiTypeResponse();
        response.setId(poiType.getId());
        response.setCode(poiType.getCode());
        response.setName(poiType.getName());
        response.setIcon(poiType.getIcon());
        response.setCreatedAt(poiType.getCreatedAt());
        response.setUpdatedAt(poiType.getUpdatedAt());
        return response;
    }
}