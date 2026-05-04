package com.travelapp.city.service;

import com.travelapp.city.exception.ConflictException;
import com.travelapp.city.exception.ResourceNotFoundException;
import com.travelapp.city.mapper.CityMapper;
import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.model.entity.City;
import com.travelapp.city.repository.CityRepository;
import com.travelapp.city.service.impl.CityServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CityServiceImplTest {

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CityMapper cityMapper;

    @InjectMocks
    private CityServiceImpl cityService;

    @Test
    void createCity_shouldNormalizeRequestAndSaveCity_whenSlugIsUnique() {
        CityRequestDto request = validRequest();
        request.setName("  Ульяновск  ");
        request.setCountry("  Россия  ");
        request.setCountryCode(" ru ");
        request.setSlug(" ulyanovsk ");
        request.setTimeZone(" Europe/Samara ");

        City entity = cityEntity(null, "Ульяновск", "ulyanovsk");
        City savedEntity = cityEntity(1L, "Ульяновск", "ulyanovsk");
        CityResponseDto response = cityResponse(1L, "Ульяновск", "ulyanovsk");

        when(cityRepository.existsBySlug("ulyanovsk")).thenReturn(false);
        when(cityMapper.toEntity(request)).thenReturn(entity);
        when(cityRepository.save(entity)).thenReturn(savedEntity);
        when(cityMapper.toDto(savedEntity)).thenReturn(response);

        CityResponseDto result = cityService.createCity(request);

        assertThat(result).isEqualTo(response);
        assertThat(request.getName()).isEqualTo("Ульяновск");
        assertThat(request.getCountry()).isEqualTo("Россия");
        assertThat(request.getCountryCode()).isEqualTo("RU");
        assertThat(request.getSlug()).isEqualTo("ulyanovsk");
        assertThat(request.getTimeZone()).isEqualTo("Europe/Samara");

        verify(cityRepository).existsBySlug("ulyanovsk");
        verify(cityRepository).save(entity);
    }

    @Test
    void createCity_shouldThrowConflictException_whenSlugAlreadyExists() {
        CityRequestDto request = validRequest();
        request.setSlug("ulyanovsk");

        when(cityRepository.existsBySlug("ulyanovsk")).thenReturn(true);

        assertThatThrownBy(() -> cityService.createCity(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Город с таким slug уже существует");

        verify(cityRepository, never()).save(any());
    }

    @Test
    void createCity_shouldThrowIllegalArgumentException_whenTimeZoneIsInvalid() {
        CityRequestDto request = validRequest();
        request.setTimeZone("Invalid/Timezone");

        assertThatThrownBy(() -> cityService.createCity(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Некорректная таймзона");

        verify(cityRepository, never()).existsBySlug(any());
        verify(cityRepository, never()).save(any());
    }

    @Test
    void updateCity_shouldUpdateExistingCity_whenSlugIsNotUsedByAnotherCity() {
        Long cityId = 1L;
        CityRequestDto request = validRequest();
        request.setName("  Казань  ");
        request.setSlug(" kazan ");
        request.setCountryCode(" ru ");

        City existingCity = cityEntity(cityId, "Ульяновск", "ulyanovsk");
        City updatedCity = cityEntity(cityId, "Казань", "kazan");
        CityResponseDto response = cityResponse(cityId, "Казань", "kazan");

        when(cityRepository.findById(cityId)).thenReturn(Optional.of(existingCity));
        when(cityRepository.existsBySlugAndIdNot("kazan", cityId)).thenReturn(false);
        when(cityRepository.save(existingCity)).thenReturn(updatedCity);
        when(cityMapper.toDto(updatedCity)).thenReturn(response);

        CityResponseDto result = cityService.updateCity(cityId, request);

        assertThat(result).isEqualTo(response);
        assertThat(request.getName()).isEqualTo("Казань");
        assertThat(request.getCountryCode()).isEqualTo("RU");
        assertThat(request.getSlug()).isEqualTo("kazan");

        verify(cityMapper).updateEntity(request, existingCity);
        verify(cityRepository).save(existingCity);
    }

    @Test
    void updateCity_shouldThrowResourceNotFoundException_whenCityDoesNotExist() {
        CityRequestDto request = validRequest();
        Long cityId = 404L;

        when(cityRepository.findById(cityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cityService.updateCity(cityId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Город с ID 404 не найден");

        verify(cityRepository, never()).save(any());
    }

    @Test
    void updateCity_shouldThrowConflictException_whenSlugIsUsedByAnotherCity() {
        Long cityId = 1L;
        CityRequestDto request = validRequest();
        request.setSlug("moscow");

        City existingCity = cityEntity(cityId, "Ульяновск", "ulyanovsk");

        when(cityRepository.findById(cityId)).thenReturn(Optional.of(existingCity));
        when(cityRepository.existsBySlugAndIdNot("moscow", cityId)).thenReturn(true);

        assertThatThrownBy(() -> cityService.updateCity(cityId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Город с таким slug уже существует");

        verify(cityRepository, never()).save(any());
    }

    @Test
    void getCityById_shouldReturnCity_whenCityExists() {
        Long cityId = 1L;
        City city = cityEntity(cityId, "Ульяновск", "ulyanovsk");
        CityResponseDto response = cityResponse(cityId, "Ульяновск", "ulyanovsk");

        when(cityRepository.findById(cityId)).thenReturn(Optional.of(city));
        when(cityMapper.toDto(city)).thenReturn(response);

        CityResponseDto result = cityService.getCityById(cityId);

        assertThat(result).isEqualTo(response);
        verify(cityRepository).findById(cityId);
    }

    @Test
    void deleteCity_shouldDeleteCity_whenCityExists() {
        Long cityId = 1L;
        when(cityRepository.existsById(cityId)).thenReturn(true);

        cityService.deleteCity(cityId);

        verify(cityRepository).deleteById(cityId);
    }

    @Test
    void deleteCity_shouldThrowResourceNotFoundException_whenCityDoesNotExist() {
        Long cityId = 404L;
        when(cityRepository.existsById(cityId)).thenReturn(false);

        assertThatThrownBy(() -> cityService.deleteCity(cityId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Город с ID 404 не найден");

        verify(cityRepository, never()).deleteById(any());
    }

    @Test
    void getCitiesByCountryCode_shouldNormalizeCountryCodeBeforeSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        City city = cityEntity(1L, "Ульяновск", "ulyanovsk");
        CityResponseDto response = cityResponse(1L, "Ульяновск", "ulyanovsk");
        Page<City> cityPage = new PageImpl<>(List.of(city), pageable, 1);

        when(cityRepository.findByCountryCodeIgnoreCase("RU", pageable)).thenReturn(cityPage);
        when(cityMapper.toDto(city)).thenReturn(response);

        Page<CityResponseDto> result = cityService.getCitiesByCountryCode(" ru ", pageable);

        assertThat(result.getContent()).containsExactly(response);
        verify(cityRepository).findByCountryCodeIgnoreCase("RU", pageable);
    }

    @Test
    void getCitiesByIds_shouldReturnEmptyList_whenIdsAreEmpty() {
        List<CityResponseDto> result = cityService.getCitiesByIds(List.of());

        assertThat(result).isEmpty();
        verify(cityRepository, never()).findAllById(any());
    }

    private CityRequestDto validRequest() {
        CityRequestDto dto = new CityRequestDto();
        dto.setName("Ульяновск");
        dto.setCountry("Россия");
        dto.setDescription("Город на Волге");
        dto.setCenterLat(new BigDecimal("54.3142"));
        dto.setCenterLng(new BigDecimal("48.4031"));
        dto.setIsPopular(true);
        dto.setSlug("ulyanovsk");
        dto.setCountryCode("RU");
        dto.setTimeZone("Europe/Samara");
        dto.setImageUrl("https://example.com/ulyanovsk.jpg");
        dto.setImageUrls(List.of("https://example.com/ulyanovsk-1.jpg"));
        return dto;
    }

    private City cityEntity(Long id, String name, String slug) {
        return City.builder()
                .id(id)
                .name(name)
                .country("Россия")
                .description("Город на Волге")
                .centerLat(new BigDecimal("54.3142"))
                .centerLng(new BigDecimal("48.4031"))
                .isPopular(true)
                .slug(slug)
                .countryCode("RU")
                .timeZone("Europe/Samara")
                .imageUrl("https://example.com/ulyanovsk.jpg")
                .imageUrls(List.of("https://example.com/ulyanovsk-1.jpg"))
                .build();
    }

    private CityResponseDto cityResponse(Long id, String name, String slug) {
        return CityResponseDto.builder()
                .id(id)
                .name(name)
                .country("Россия")
                .description("Город на Волге")
                .centerLat(new BigDecimal("54.3142"))
                .centerLng(new BigDecimal("48.4031"))
                .isPopular(true)
                .slug(slug)
                .countryCode("RU")
                .timeZone("Europe/Samara")
                .imageUrl("https://example.com/ulyanovsk.jpg")
                .imageUrls(List.of("https://example.com/ulyanovsk-1.jpg"))
                .build();
    }
}