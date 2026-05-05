package com.travelapp.personalization.service;

import com.travelapp.personalization.client.CityClient;
import com.travelapp.personalization.exception.ResourceNotFoundException;
import com.travelapp.personalization.model.dto.external.CityDto;
import com.travelapp.personalization.model.dto.request.PresetFilterRequest;
import com.travelapp.personalization.model.dto.response.PresetFilterResponse;
import com.travelapp.personalization.model.entity.PresetFilter;
import com.travelapp.personalization.repository.PresetFilterRepository;
import com.travelapp.personalization.service.impl.PresetFilterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresetFilterServiceImplTest {

    @Mock
    private PresetFilterRepository presetFilterRepository;

    @Mock
    private CityClient cityClient;

    @InjectMocks
    private PresetFilterServiceImpl presetFilterService;

    @Test
    void createPresetFilter_shouldSavePreset_whenCityExistsAndNameIsUnique() {
        Long userId = 10L;
        PresetFilterRequest request = request("Музеи рядом", 1L, 2L);
        PresetFilter saved = preset(1L, userId, request.getName(), request.getCityId(), request.getPoiTypeId());

        when(cityClient.getCityById(1L)).thenReturn(city(1L));
        when(presetFilterRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(false);
        when(presetFilterRepository.save(any(PresetFilter.class))).thenReturn(saved);

        PresetFilterResponse result = presetFilterService.createPresetFilter(userId, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Музеи рядом");
        assertThat(result.getCityId()).isEqualTo(1L);
        assertThat(result.getPoiTypeId()).isEqualTo(2L);

        verify(presetFilterRepository).save(argThat(filter ->
                filter.getUserId().equals(userId)
                        && filter.getName().equals("Музеи рядом")
                        && filter.getFiltersJson().equals(request.getFiltersJson())
        ));
    }

    @Test
    void createPresetFilter_shouldThrowResourceNotFoundException_whenCityDoesNotExist() {
        Long userId = 10L;
        PresetFilterRequest request = request("Музеи рядом", 404L, 2L);

        when(cityClient.getCityById(404L)).thenThrow(new RuntimeException("not found"));

        assertThatThrownBy(() -> presetFilterService.createPresetFilter(userId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("City not found");

        verify(presetFilterRepository, never()).save(any(PresetFilter.class));
    }

    @Test
    void createPresetFilter_shouldThrowIllegalStateException_whenNameAlreadyExists() {
        Long userId = 10L;
        PresetFilterRequest request = request("Музеи рядом", 1L, 2L);

        when(cityClient.getCityById(1L)).thenReturn(city(1L));
        when(presetFilterRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(true);

        assertThatThrownBy(() -> presetFilterService.createPresetFilter(userId, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");

        verify(presetFilterRepository, never()).save(any(PresetFilter.class));
    }

    @Test
    void updatePresetFilter_shouldUpdatePreset_whenUserOwnsItAndNameIsUnique() {
        Long userId = 10L;
        Long filterId = 1L;
        PresetFilter existing = preset(filterId, userId, "Старое имя", 1L, 2L);
        PresetFilterRequest request = request("Новое имя", 3L, 4L);

        when(presetFilterRepository.findById(filterId)).thenReturn(Optional.of(existing));
        when(cityClient.getCityById(3L)).thenReturn(city(3L));
        when(presetFilterRepository.existsByUserIdAndName(userId, request.getName())).thenReturn(false);
        when(presetFilterRepository.save(existing)).thenReturn(existing);

        PresetFilterResponse result = presetFilterService.updatePresetFilter(userId, filterId, request);

        assertThat(result.getName()).isEqualTo("Новое имя");
        assertThat(result.getCityId()).isEqualTo(3L);
        assertThat(result.getPoiTypeId()).isEqualTo(4L);
        verify(presetFilterRepository).save(existing);
    }

    @Test
    void updatePresetFilter_shouldThrowResourceNotFoundException_whenUserDoesNotOwnPreset() {
        Long filterId = 1L;
        PresetFilter existing = preset(filterId, 99L, "Чужой пресет", 1L, 2L);
        PresetFilterRequest request = request("Новое имя", 1L, 2L);

        when(presetFilterRepository.findById(filterId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> presetFilterService.updatePresetFilter(10L, filterId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Preset filter not found");

        verify(presetFilterRepository, never()).save(any(PresetFilter.class));
    }

    @Test
    void deletePresetFilter_shouldDeletePreset_whenUserOwnsIt() {
        Long userId = 10L;
        Long filterId = 1L;
        PresetFilter existing = preset(filterId, userId, "Музеи", 1L, 2L);

        when(presetFilterRepository.findById(filterId)).thenReturn(Optional.of(existing));

        presetFilterService.deletePresetFilter(userId, filterId);

        verify(presetFilterRepository).delete(existing);
    }

    @Test
    void getPresetFilter_shouldReturnPreset_whenUserOwnsIt() {
        Long userId = 10L;
        Long filterId = 1L;
        PresetFilter existing = preset(filterId, userId, "Музеи", 1L, 2L);

        when(presetFilterRepository.findById(filterId)).thenReturn(Optional.of(existing));

        PresetFilterResponse result = presetFilterService.getPresetFilter(userId, filterId);

        assertThat(result.getId()).isEqualTo(filterId);
        assertThat(result.getName()).isEqualTo("Музеи");
    }

    @Test
    void getUserPresetFilters_shouldReturnMappedPage() {
        Long userId = 10L;
        Pageable pageable = PageRequest.of(0, 10);
        PresetFilter preset = preset(1L, userId, "Музеи", 1L, 2L);

        when(presetFilterRepository.findByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(preset), pageable, 1));

        Page<PresetFilterResponse> result = presetFilterService.getUserPresetFilters(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Музеи");
    }

    @Test
    void getPresetFiltersForContext_shouldReturnMappedList() {
        Long userId = 10L;
        PresetFilter preset = preset(1L, userId, "Музеи", 1L, 2L);

        when(presetFilterRepository.findByUserIdAndCityIdAndPoiTypeId(userId, 1L, 2L))
                .thenReturn(List.of(preset));

        List<PresetFilterResponse> result = presetFilterService.getPresetFiltersForContext(userId, 1L, 2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Музеи");
    }

    @Test
    void getPresetFilterByName_shouldReturnPreset_whenExists() {
        Long userId = 10L;
        PresetFilter preset = preset(1L, userId, "Музеи", 1L, 2L);

        when(presetFilterRepository.findByUserIdAndName(userId, "Музеи")).thenReturn(Optional.of(preset));

        PresetFilterResponse result = presetFilterService.getPresetFilterByName(userId, "Музеи");

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getPresetFilterByName_shouldThrowResourceNotFoundException_whenPresetDoesNotExist() {
        when(presetFilterRepository.findByUserIdAndName(10L, "Нет такого")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> presetFilterService.getPresetFilterByName(10L, "Нет такого"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Preset filter not found");
    }

    private PresetFilterRequest request(String name, Long cityId, Long poiTypeId) {
        return new PresetFilterRequest(name, "{\"ratingFrom\":4,\"openNow\":true}", cityId, poiTypeId);
    }

    private PresetFilter preset(Long id, Long userId, String name, Long cityId, Long poiTypeId) {
        return PresetFilter.builder()
                .id(id)
                .name(name)
                .filtersJson("{\"ratingFrom\":4,\"openNow\":true}")
                .userId(userId)
                .cityId(cityId)
                .poiTypeId(poiTypeId)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 2, 12, 0))
                .build();
    }

    private CityDto city(Long id) {
        CityDto city = new CityDto();
        city.setId(id);
        city.setName("Ульяновск");
        city.setSlug("ulyanovsk");
        city.setCountry("Россия");
        return city;
    }
}
