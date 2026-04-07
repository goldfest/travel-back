package com.travelapp.personalization.service;

import com.travelapp.personalization.model.dto.request.PresetFilterRequest;
import com.travelapp.personalization.model.dto.response.PresetFilterResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PresetFilterService {

    PresetFilterResponse createPresetFilter(Long userId, PresetFilterRequest request);

    PresetFilterResponse updatePresetFilter(Long userId, Long filterId, PresetFilterRequest request);

    void deletePresetFilter(Long userId, Long filterId);

    PresetFilterResponse getPresetFilter(Long userId, Long filterId);

    Page<PresetFilterResponse> getUserPresetFilters(Long userId, Pageable pageable);

    List<PresetFilterResponse> getPresetFiltersForContext(Long userId, Long cityId, Long poiTypeId);

    PresetFilterResponse getPresetFilterByName(Long userId, String name);
}