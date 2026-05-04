package com.travelapp.poi.service;

import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.model.dto.request.PoiTypeRequest;
import com.travelapp.poi.model.dto.response.PoiTypeResponse;
import com.travelapp.poi.model.entity.PoiType;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiTypeRepository;
import com.travelapp.poi.service.impl.PoiTypeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiTypeServiceImplTest {

    @Mock
    private PoiTypeRepository poiTypeRepository;

    @Mock
    private PoiRepository poiRepository;

    @InjectMocks
    private PoiTypeServiceImpl poiTypeService;

    @Test
    void createPoiType_shouldSaveType_whenCodeIsUnique() {
        PoiTypeRequest request = request("museum", "Музей", "museum.svg");
        when(poiTypeRepository.existsByCode("museum")).thenReturn(false);
        when(poiTypeRepository.save(any(PoiType.class))).thenAnswer(invocation -> {
            PoiType type = invocation.getArgument(0);
            type.setId(1L);
            return type;
        });

        PoiTypeResponse result = poiTypeService.createPoiType(request, 10L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("museum");
        assertThat(result.getName()).isEqualTo("Музей");
        verify(poiTypeRepository).save(any(PoiType.class));
    }

    @Test
    void createPoiType_shouldThrowValidationException_whenCodeAlreadyExists() {
        PoiTypeRequest request = request("museum", "Музей", null);
        when(poiTypeRepository.existsByCode("museum")).thenReturn(true);

        assertThatThrownBy(() -> poiTypeService.createPoiType(request, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");

        verify(poiTypeRepository, never()).save(any());
    }

    @Test
    void updatePoiType_shouldUpdateType_whenTypeExistsAndCodeIsFree() {
        PoiType existing = type(1L, "museum", "Музей");
        PoiTypeRequest request = request("park", "Парк", "park.svg");

        when(poiTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(poiTypeRepository.existsByCodeAndIdNot("park", 1L)).thenReturn(false);
        when(poiTypeRepository.save(existing)).thenReturn(existing);

        PoiTypeResponse result = poiTypeService.updatePoiType(1L, request, 10L);

        assertThat(result.getCode()).isEqualTo("park");
        assertThat(result.getName()).isEqualTo("Парк");
        assertThat(existing.getIcon()).isEqualTo("park.svg");
    }

    @Test
    void updatePoiType_shouldThrowPoiTypeNotFoundException_whenTypeDoesNotExist() {
        when(poiTypeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiTypeService.updatePoiType(404L, request("park", "Парк", null), 10L))
                .isInstanceOf(PoiTypeNotFoundException.class);

        verify(poiTypeRepository, never()).save(any());
    }

    @Test
    void deletePoiType_shouldDeleteType_whenItIsNotUsed() {
        PoiType existing = type(1L, "museum", "Музей");
        when(poiTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(poiRepository.countByPoiTypeId(1L)).thenReturn(0L);

        poiTypeService.deletePoiType(1L, 10L);

        verify(poiTypeRepository).delete(existing);
    }

    @Test
    void deletePoiType_shouldThrowValidationException_whenTypeIsUsedByPoi() {
        PoiType existing = type(1L, "museum", "Музей");
        when(poiTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(poiRepository.countByPoiTypeId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> poiTypeService.deletePoiType(1L, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("used by 3 POI");

        verify(poiTypeRepository, never()).delete(any());
    }

    @Test
    void getPoiTypeByCode_shouldReturnType_whenCodeExists() {
        when(poiTypeRepository.findByCode("museum")).thenReturn(Optional.of(type(1L, "museum", "Музей")));

        PoiTypeResponse result = poiTypeService.getPoiTypeByCode("museum");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCode()).isEqualTo("museum");
    }

    @Test
    void getPoiTypes_shouldReturnPagedResponse() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<PoiType> page = new PageImpl<>(List.of(type(1L, "museum", "Музей")), pageable, 1);
        when(poiTypeRepository.findAll(pageable)).thenReturn(page);

        Page<PoiTypeResponse> result = poiTypeService.getPoiTypes(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("museum");
    }

    private PoiTypeRequest request(String code, String name, String icon) {
        PoiTypeRequest request = new PoiTypeRequest();
        request.setCode(code);
        request.setName(name);
        request.setIcon(icon);
        return request;
    }

    private PoiType type(Long id, String code, String name) {
        PoiType type = new PoiType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        return type;
    }
}
