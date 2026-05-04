package com.travelapp.poi.service;

import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.mapper.PoiMapper;
import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.model.entity.PoiFeature;
import com.travelapp.poi.model.entity.PoiHours;
import com.travelapp.poi.model.entity.PoiMedia;
import com.travelapp.poi.model.entity.PoiSource;
import com.travelapp.poi.model.entity.PoiType;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiTypeRepository;
import com.travelapp.poi.service.command.PoiCommandServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PoiCommandServiceImplTest {

    @Mock
    private PoiRepository poiRepository;

    @Mock
    private PoiTypeRepository poiTypeRepository;

    @Mock
    private PoiMapper poiMapper;

    @InjectMocks
    private PoiCommandServiceImpl poiCommandService;

    @Test
    void createPoi_shouldSaveUnverifiedPoiWithNestedData_whenRequestIsValid() {
        Long userId = 10L;
        PoiCreateRequest request = validCreateRequest();
        PoiType poiType = poiType(1L, "museum", "Музей");
        PoiResponse response = response(100L, request.getName(), request.getSlug());

        when(poiTypeRepository.findById(request.getPoiTypeId())).thenReturn(Optional.of(poiType));
        when(poiRepository.findBySlug(request.getSlug())).thenReturn(Optional.empty());
        when(poiRepository.save(any(Poi.class))).thenAnswer(invocation -> {
            Poi poi = invocation.getArgument(0);
            poi.setId(100L);
            return poi;
        });
        when(poiMapper.toResponse(any(Poi.class))).thenReturn(response);

        PoiResponse result = poiCommandService.createPoi(request, userId);

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<Poi> captor = ArgumentCaptor.forClass(Poi.class);
        verify(poiRepository).save(captor.capture());
        Poi saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Ульяновский областной краеведческий музей");
        assertThat(saved.getSlug()).isEqualTo("ulyanovsk-kraevedcheskiy-muzey");
        assertThat(saved.getCityId()).isEqualTo(1L);
        assertThat(saved.getPoiType()).isEqualTo(poiType);
        assertThat(saved.getCreatedBy()).isEqualTo(userId);
        assertThat(saved.getIsVerified()).isFalse();
        assertThat(saved.getDescription()).isEqualTo("Исторический музей с экспозициями о культуре и развитии Ульяновской области.");

        assertThat(saved.getFeatures())
                .extracting(PoiFeature::getKey)
                .containsExactlyInAnyOrder("wheelchair");
        assertThat(saved.getHours()).hasSize(1);
        PoiHours hours = saved.getHours().iterator().next();
        assertThat(hours.getDayOfWeek()).isEqualTo((short) 1);
        assertThat(hours.getOpenTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(hours.getCloseTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(hours.getPoi()).isEqualTo(saved);

        PoiMedia media = saved.getMedia().iterator().next();
        assertThat(media.getUrl()).isEqualTo("https://upload.wikimedia.org/test.jpg");
        assertThat(media.getSourceType()).isEqualTo(PoiMedia.SourceType.SYSTEM_WIKIMEDIA);
        assertThat(media.getModerationStatus()).isEqualTo(PoiMedia.ModerationStatus.APPROVED);
        assertThat(media.getUserId()).isEqualTo(userId);

        PoiSource source = saved.getSources().iterator().next();
        assertThat(source.getSourceCode()).isEqualTo("WIKIPEDIA");
        assertThat(source.getExternalId()).isEqualTo("wiki-1");
        assertThat(source.getPoi()).isEqualTo(saved);
    }

    @Test
    void createPoi_shouldThrowPoiTypeNotFoundException_whenTypeDoesNotExist() {
        PoiCreateRequest request = validCreateRequest();
        when(poiTypeRepository.findById(request.getPoiTypeId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiCommandService.createPoi(request, 10L))
                .isInstanceOf(PoiTypeNotFoundException.class);

        verify(poiRepository, never()).save(any());
    }

    @Test
    void createPoi_shouldThrowValidationException_whenSlugAlreadyExists() {
        PoiCreateRequest request = validCreateRequest();
        when(poiTypeRepository.findById(request.getPoiTypeId())).thenReturn(Optional.of(poiType(1L, "museum", "Музей")));
        when(poiRepository.findBySlug(request.getSlug())).thenReturn(Optional.of(new Poi()));

        assertThatThrownBy(() -> poiCommandService.createPoi(request, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Slug already exists");

        verify(poiRepository, never()).save(any());
    }

    @Test
    void createPoi_shouldThrowValidationException_whenDescriptionIsTooShort() {
        PoiCreateRequest request = validCreateRequest();
        request.setDescription("Слишком коротко");

        when(poiTypeRepository.findById(request.getPoiTypeId())).thenReturn(Optional.of(poiType(1L, "museum", "Музей")));
        when(poiRepository.findBySlug(request.getSlug())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiCommandService.createPoi(request, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Description is too short");

        verify(poiRepository, never()).save(any());
    }

    @Test
    void createPoi_shouldThrowValidationException_whenSiteUrlHasInvalidProtocol() {
        PoiCreateRequest request = validCreateRequest();
        request.setSiteUrl("ftp://example.com");

        when(poiTypeRepository.findById(request.getPoiTypeId())).thenReturn(Optional.of(poiType(1L, "museum", "Музей")));
        when(poiRepository.findBySlug(request.getSlug())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiCommandService.createPoi(request, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Site URL must start with http:// or https://");

        verify(poiRepository, never()).save(any());
    }

    @Test
    void updatePoi_shouldUpdateSimpleFieldsAndCleanHtml_whenPoiExists() {
        Long poiId = 100L;
        PoiUpdateRequest request = new PoiUpdateRequest();
        request.setName("Обновлённый музей");
        request.setSlug("updated-museum");
        request.setDescription("<p>Новое подробное описание объекта культуры города.</p><script>alert(1)</script>");
        request.setLatitude(new BigDecimal("54.310000"));
        request.setLongitude(new BigDecimal("48.400000"));
        request.setPriceLevel((short) 2);
        request.setFeatures(Map.of("parking", "yes"));
        request.setHours(List.of(hours((short) 2, "09:00", "17:30", false)));

        Poi existing = poi(100L, "Старый музей", "old-museum", 10L);
        PoiResponse response = response(poiId, request.getName(), request.getSlug());

        when(poiRepository.findById(poiId)).thenReturn(Optional.of(existing));
        when(poiRepository.existsBySlugAndIdNot("updated-museum", poiId)).thenReturn(false);
        when(poiRepository.save(existing)).thenReturn(existing);
        when(poiMapper.toResponse(existing)).thenReturn(response);

        PoiResponse result = poiCommandService.updatePoi(poiId, request, 10L);

        assertThat(result).isEqualTo(response);
        assertThat(existing.getName()).isEqualTo("Обновлённый музей");
        assertThat(existing.getSlug()).isEqualTo("updated-museum");
        assertThat(existing.getDescription()).isEqualTo("Новое подробное описание объекта культуры города.");
        assertThat(existing.getLatitude()).isEqualByComparingTo("54.310000");
        assertThat(existing.getPriceLevel()).isEqualTo((short) 2);
        assertThat(existing.getFeatures()).hasSize(1);
        assertThat(existing.getHours()).hasSize(1);
    }

    @Test
    void updatePoi_shouldThrowPoiNotFoundException_whenPoiDoesNotExist() {
        Long poiId = 404L;
        when(poiRepository.findById(poiId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiCommandService.updatePoi(poiId, new PoiUpdateRequest(), 10L))
                .isInstanceOf(PoiNotFoundException.class);

        verify(poiRepository, never()).save(any());
    }

    @Test
    void updatePoi_shouldThrowValidationException_whenSlugIsUsedByAnotherPoi() {
        Long poiId = 100L;
        PoiUpdateRequest request = new PoiUpdateRequest();
        request.setSlug("used-slug");

        Poi existing = poi(poiId, "Музей", "old-slug", 10L);

        when(poiRepository.findById(poiId)).thenReturn(Optional.of(existing));
        when(poiRepository.existsBySlugAndIdNot("used-slug", poiId)).thenReturn(true);

        assertThatThrownBy(() -> poiCommandService.updatePoi(poiId, request, 10L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Slug already exists");

        verify(poiRepository, never()).save(any());
    }

    @Test
    void deletePoi_shouldDeletePoi_whenUserIsOwner() {
        Long poiId = 100L;
        Long userId = 10L;
        Poi poi = poi(poiId, "Музей", "museum", userId);
        when(poiRepository.findById(poiId)).thenReturn(Optional.of(poi));

        poiCommandService.deletePoi(poiId, userId);

        verify(poiRepository).delete(poi);
    }

    @Test
    void deletePoi_shouldThrowValidationException_whenUserIsNotOwnerAndNotAdmin() {
        Long poiId = 100L;
        Poi poi = poi(poiId, "Музей", "museum", 10L);
        when(poiRepository.findById(poiId)).thenReturn(Optional.of(poi));

        assertThatThrownBy(() -> poiCommandService.deletePoi(poiId, 99L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("permission");

        verify(poiRepository, never()).delete(any(Poi.class));
    }

    @Test
    void deletePoi_shouldThrowPoiNotFoundException_whenPoiDoesNotExist() {
        Long poiId = 404L;
        when(poiRepository.findById(poiId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> poiCommandService.deletePoi(poiId, 10L))
                .isInstanceOf(PoiNotFoundException.class);

        verify(poiRepository, never()).delete(any(Poi.class));
    }

    private PoiCreateRequest validCreateRequest() {
        PoiCreateRequest request = new PoiCreateRequest();
        request.setName("Ульяновский областной краеведческий музей");
        request.setSlug("ulyanovsk-kraevedcheskiy-muzey");
        request.setCityId(1L);
        request.setPoiTypeId(1L);
        request.setLatitude(new BigDecimal("54.314200"));
        request.setLongitude(new BigDecimal("48.403100"));
        request.setAddress("бульвар Новый Венец, 3/4");
        request.setDescription("<p>Исторический музей с экспозициями о культуре и развитии Ульяновской области.</p>");
        request.setPhone("+78422123456");
        request.setSiteUrl("https://example.com");
        request.setPriceLevel((short) 1);
        request.setFeatures(Map.of(" wheelchair ", "yes", " ", "ignored"));
        request.setHours(List.of(hours((short) 1, "10:00", "18:00", false)));
        request.setMedia(List.of(media("https://upload.wikimedia.org/test.jpg", PoiMedia.MediaType.PHOTO)));
        request.setSources(List.of(source("WIKIPEDIA", "https://ru.wikipedia.org/wiki/test", "wiki-1")));
        return request;
    }

    private PoiCreateRequest.HoursRequest hours(short day, String open, String close, boolean aroundTheClock) {
        PoiCreateRequest.HoursRequest hours = new PoiCreateRequest.HoursRequest();
        hours.setDayOfWeek(day);
        hours.setOpenTime(open != null ? LocalTime.parse(open) : null);
        hours.setCloseTime(close != null ? LocalTime.parse(close) : null);
        hours.setAroundTheClock(aroundTheClock);
        return hours;
    }

    private PoiCreateRequest.MediaRequest media(String url, PoiMedia.MediaType mediaType) {
        PoiCreateRequest.MediaRequest media = new PoiCreateRequest.MediaRequest();
        media.setUrl(url);
        media.setMediaType(mediaType);
        return media;
    }

    private PoiCreateRequest.SourceRequest source(String code, String url, String externalId) {
        PoiCreateRequest.SourceRequest source = new PoiCreateRequest.SourceRequest();
        source.setSourceCode(code);
        source.setSourceUrl(url);
        source.setExternalId(externalId);
        source.setConfidenceScore(new BigDecimal("0.85"));
        return source;
    }

    private PoiType poiType(Long id, String code, String name) {
        PoiType type = new PoiType();
        type.setId(id);
        type.setCode(code);
        type.setName(name);
        return type;
    }

    private Poi poi(Long id, String name, String slug, Long createdBy) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setSlug(slug);
        poi.setCityId(1L);
        poi.setPoiType(poiType(1L, "museum", "Музей"));
        poi.setLatitude(new BigDecimal("54.314200"));
        poi.setLongitude(new BigDecimal("48.403100"));
        poi.setDescription("Старое описание объекта культуры города.");
        poi.setCreatedBy(createdBy);
        poi.setIsVerified(false);
        poi.setIsClosed(false);
        return poi;
    }

    private PoiResponse response(Long id, String name, String slug) {
        PoiResponse response = new PoiResponse();
        response.setId(id);
        response.setName(name);
        response.setSlug(slug);
        return response;
    }
}
