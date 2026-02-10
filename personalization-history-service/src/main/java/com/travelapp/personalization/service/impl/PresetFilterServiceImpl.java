package com.travelapp.personalization.service.impl;

import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.request.PresetFilterRequest;
import com.travelapp.personalization.model.dto.response.PresetFilterResponse;
import com.travelapp.personalization.model.entity.PresetFilter;
import com.travelapp.personalization.repository.PresetFilterRepository;
import com.travelapp.personalization.service.PresetFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PresetFilterServiceImpl implements PresetFilterService {

    private final PresetFilterRepository presetFilterRepository;

    @Override
    @CacheEvict(value = "presetFilters", key = "#userId")
    public PresetFilterResponse createPresetFilter(Long userId, PresetFilterRequest request) {
        log.info("Creating preset filter '{}' for user {}", request.getName(), userId);

        // Проверяем уникальность названия для пользователя
        if (presetFilterRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new IllegalStateException("Preset filter with this name already exists");
        }

        PresetFilter presetFilter = PresetFilter.builder()
                .name(request.getName())
                .filtersJson(request.getFiltersJson())
                .userId(userId)
                .cityId(request.getCityId())
                .poiTypeId(request.getPoiTypeId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        PresetFilter saved = presetFilterRepository.save(presetFilter);
        log.debug("Preset filter created with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    @CacheEvict(value = {"presetFilters", "presetFilter"}, key = "#userId")
    public PresetFilterResponse updatePresetFilter(Long userId, Long filterId, PresetFilterRequest request) {
        log.info("Updating preset filter {} for user {}", filterId, userId);

        PresetFilter presetFilter = getPresetFilterEntity(userId, filterId);

        // Проверяем уникальность нового названия (если оно изменилось)
        if (!presetFilter.getName().equals(request.getName()) &&
                presetFilterRepository.existsByUserIdAndName(userId, request.getName())) {
            throw new IllegalStateException("Preset filter with this name already exists");
        }

        presetFilter.setName(request.getName());
        presetFilter.setFiltersJson(request.getFiltersJson());
        presetFilter.setCityId(request.getCityId());
        presetFilter.setPoiTypeId(request.getPoiTypeId());
        presetFilter.setUpdatedAt(LocalDateTime.now());

        PresetFilter updated = presetFilterRepository.save(presetFilter);

        return mapToResponse(updated);
    }

    @Override
    @CacheEvict(value = {"presetFilters", "presetFilter"}, key = "#userId")
    public void deletePresetFilter(Long userId, Long filterId) {
        log.info("Deleting preset filter {} for user {}", filterId, userId);

        PresetFilter presetFilter = getPresetFilterEntity(userId, filterId);
        presetFilterRepository.delete(presetFilter);

        log.debug("Preset filter deleted successfully");
    }

    @Override
    @Cacheable(value = "presetFilter", key = "#filterId")
    @Transactional(readOnly = true)
    public PresetFilterResponse getPresetFilter(Long userId, Long filterId) {
        log.info("Getting preset filter {} for user {}", filterId, userId);

        PresetFilter presetFilter = getPresetFilterEntity(userId, filterId);
        return mapToResponse(presetFilter);
    }

    @Override
    @Cacheable(value = "presetFilters", key = "#userId + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public Page<PresetFilterResponse> getUserPresetFilters(Long userId, Pageable pageable) {
        log.info("Fetching preset filters for user {}", userId);

        return presetFilterRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PresetFilterResponse> getPresetFiltersForContext(Long userId, Long cityId, Long poiTypeId) {
        log.info("Fetching preset filters for user {} in context: city={}, type={}",
                userId, cityId, poiTypeId);

        return presetFilterRepository.findByUserIdAndCityIdAndPoiTypeId(userId, cityId, poiTypeId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PresetFilterResponse getPresetFilterByName(Long userId, String name) {
        log.info("Getting preset filter '{}' for user {}", name, userId);

        PresetFilter presetFilter = presetFilterRepository.findByUserIdAndName(userId, name)
                .orElseThrow(() -> new ResourceNotFoundException("Preset filter not found"));

        return mapToResponse(presetFilter);
    }

    private PresetFilter getPresetFilterEntity(Long userId, Long filterId) {
        return presetFilterRepository.findById(filterId)
                .filter(filter -> filter.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Preset filter not found"));
    }

    private PresetFilterResponse mapToResponse(PresetFilter presetFilter) {
        return PresetFilterResponse.builder()
                .id(presetFilter.getId())
                .name(presetFilter.getName())
                .filtersJson(presetFilter.getFiltersJson())
                .userId(presetFilter.getUserId())
                .cityId(presetFilter.getCityId())
                .poiTypeId(presetFilter.getPoiTypeId())
                .createdAt(presetFilter.getCreatedAt())
                .updatedAt(presetFilter.getUpdatedAt())
                .build();
    }
}