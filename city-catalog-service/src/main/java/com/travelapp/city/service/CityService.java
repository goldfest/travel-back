package com.travelapp.city.service;

import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CityService {

    CityResponseDto createCity(CityRequestDto requestDto);

    CityResponseDto updateCity(Long id, CityRequestDto requestDto);

    CityResponseDto getCityById(Long id);

    CityResponseDto getCityBySlug(String slug);

    void deleteCity(Long id);

    Page<CityResponseDto> getAllCities(Pageable pageable);

    List<CityResponseDto> getPopularCities();

    Page<CityResponseDto> getCitiesByPopularity(Boolean isPopular, Pageable pageable);

    Page<CityResponseDto> searchCities(String query, Pageable pageable);

    Page<CityResponseDto> getCitiesByCountryCode(String countryCode, Pageable pageable);

    boolean existsBySlug(String slug);

    List<CityResponseDto> getCitiesByIds(List<Long> ids);
}