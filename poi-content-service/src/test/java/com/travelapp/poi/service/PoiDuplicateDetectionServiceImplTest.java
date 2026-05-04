package com.travelapp.poi.service;

import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.model.entity.PoiSource;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiSourceRepository;
import com.travelapp.poi.service.impl.PoiDuplicateDetectionServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiDuplicateDetectionServiceImplTest {

    @Mock
    private PoiRepository poiRepository;

    @Mock
    private PoiSourceRepository poiSourceRepository;

    @InjectMocks
    private PoiDuplicateDetectionServiceImpl duplicateDetectionService;

    @Test
    void findDuplicate_shouldReturnEmpty_whenRequestIsNull() {
        Optional<Poi> result = duplicateDetectionService.findDuplicate(null);

        assertThat(result).isEmpty();
        verify(poiRepository, never()).findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(any(), any(), any());
    }

    @Test
    void findDuplicate_shouldFindByExternalId_first() {
        Poi existingPoi = poi(1L, "Музей");
        PoiSource source = new PoiSource();
        source.setPoi(existingPoi);

        PoiCreateRequest request = request();
        request.setSources(List.of(sourceRequest("TWO_GIS", null, "70000001080285364")));

        when(poiSourceRepository.findFirstBySourceCodeAndExternalId("TWO_GIS", "70000001080285364"))
                .thenReturn(Optional.of(source));

        Optional<Poi> result = duplicateDetectionService.findDuplicate(request);

        assertThat(result).contains(existingPoi);
        verify(poiRepository, never()).findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(any(), any(), any());
    }

    @Test
    void findDuplicate_shouldFindBySourceUrl_whenExternalIdNotFound() {
        Poi existingPoi = poi(2L, "Музей");
        PoiSource source = new PoiSource();
        source.setPoi(existingPoi);

        PoiCreateRequest request = request();
        request.setSources(List.of(sourceRequest("WIKIPEDIA", "https://ru.wikipedia.org/wiki/test", null)));

        when(poiSourceRepository.findFirstBySourceCodeAndSourceUrl("WIKIPEDIA", "https://ru.wikipedia.org/wiki/test"))
                .thenReturn(Optional.of(source));

        Optional<Poi> result = duplicateDetectionService.findDuplicate(request);

        assertThat(result).contains(existingPoi);
        verify(poiRepository, never()).findPotentialDuplicatesByNameAndCoordinates(any(), any(), any(), any(), any());
    }

    @Test
    void findDuplicate_shouldFindByNameAddressAndCity_whenSourceDoesNotMatch() {
        Poi existingPoi = poi(3L, "Музей");
        PoiCreateRequest request = request();
        request.setName("  Музей  ");
        request.setAddress("  бульвар Новый Венец, 3/4  ");
        request.setSources(null);

        when(poiRepository.findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(
                "Музей", "бульвар Новый Венец, 3/4", 1L
        )).thenReturn(Optional.of(existingPoi));

        Optional<Poi> result = duplicateDetectionService.findDuplicate(request);

        assertThat(result).contains(existingPoi);
        verify(poiRepository).findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(
                "Музей", "бульвар Новый Венец, 3/4", 1L
        );
    }

    @Test
    void findDuplicate_shouldFindByNameAndCoordinates_whenAddressDoesNotMatch() {
        Poi existingPoi = poi(4L, "Музей");
        PoiCreateRequest request = request();
        request.setAddress(null);
        request.setSources(null);

        when(poiRepository.findPotentialDuplicatesByNameAndCoordinates(
                eq("Музей"),
                eq(new BigDecimal("54.314200")),
                eq(new BigDecimal("48.403100")),
                eq(1L),
                eq(new BigDecimal("0.001"))
        )).thenReturn(List.of(existingPoi));

        Optional<Poi> result = duplicateDetectionService.findDuplicate(request);

        assertThat(result).contains(existingPoi);
    }

    @Test
    void findDuplicate_shouldReturnEmpty_whenNoMatchFound() {
        PoiCreateRequest request = request();
        request.setSources(null);

        when(poiRepository.findFirstByNameIgnoreCaseAndAddressIgnoreCaseAndCityId(
                "Музей", "бульвар Новый Венец, 3/4", 1L
        )).thenReturn(Optional.empty());
        when(poiRepository.findPotentialDuplicatesByNameAndCoordinates(
                eq("Музей"), any(), any(), eq(1L), eq(new BigDecimal("0.001"))
        )).thenReturn(List.of());

        Optional<Poi> result = duplicateDetectionService.findDuplicate(request);

        assertThat(result).isEmpty();
    }

    private PoiCreateRequest request() {
        PoiCreateRequest request = new PoiCreateRequest();
        request.setName("Музей");
        request.setCityId(1L);
        request.setAddress("бульвар Новый Венец, 3/4");
        request.setLatitude(new BigDecimal("54.314200"));
        request.setLongitude(new BigDecimal("48.403100"));
        return request;
    }

    private PoiCreateRequest.SourceRequest sourceRequest(String code, String url, String externalId) {
        PoiCreateRequest.SourceRequest source = new PoiCreateRequest.SourceRequest();
        source.setSourceCode(code);
        source.setSourceUrl(url);
        source.setExternalId(externalId);
        return source;
    }

    private Poi poi(Long id, String name) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        return poi;
    }
}
