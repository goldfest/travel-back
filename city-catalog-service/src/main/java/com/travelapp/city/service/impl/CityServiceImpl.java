package com.travelapp.city.service.impl;

import com.travelapp.city.exception.ResourceNotFoundException;
import com.travelapp.city.mapper.CityMapper;
import com.travelapp.city.model.dto.request.CityRequestDto;
import com.travelapp.city.model.dto.response.CityResponseDto;
import com.travelapp.city.model.entity.City;
import com.travelapp.city.repository.CityRepository;
import com.travelapp.city.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;
    private final CityMapper cityMapper;

    @Override
    @Transactional
    @CacheEvict(value = {"cities", "popularCities", "cityBySlug"}, allEntries = true)
    public CityResponseDto createCity(CityRequestDto requestDto) {
        log.debug("Creating new city: {}", requestDto.getName());

        if (cityRepository.existsBySlug(requestDto.getSlug())) {
            throw new IllegalArgumentException("Город с таким slug уже существует: " + requestDto.getSlug());
        }

        City city = cityMapper.toEntity(requestDto);
        City savedCity = cityRepository.save(city);

        log.info("City created successfully: {} (ID: {})", savedCity.getName(), savedCity.getId());
        return cityMapper.toDto(savedCity);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "city", key = "#id"),
            @CacheEvict(value = "cityBySlug", allEntries = true),
            @CacheEvict(value = "cities", allEntries = true),
            @CacheEvict(value = "popularCities", allEntries = true)
    })
    public CityResponseDto updateCity(Long id, CityRequestDto requestDto) {
        log.debug("Updating city with ID: {}", id);

        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Город с ID " + id + " не найден"));

        if (cityRepository.existsBySlugAndIdNot(requestDto.getSlug(), id)) {
            throw new IllegalArgumentException("Город с таким slug уже существует: " + requestDto.getSlug());
        }

        cityMapper.updateEntity(requestDto, city);
        City updatedCity = cityRepository.save(city);

        log.info("City updated successfully: {} (ID: {})", updatedCity.getName(), updatedCity.getId());
        return cityMapper.toDto(updatedCity);
    }

    @Override
    @Cacheable(value = "city", key = "#id")
    public CityResponseDto getCityById(Long id) {
        log.debug("Getting city by ID: {}", id);

        City city = cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Город с ID " + id + " не найден"));

        return cityMapper.toDto(city);
    }

    @Override
    @Cacheable(value = "cityBySlug", key = "#slug")
    public CityResponseDto getCityBySlug(String slug) {
        log.debug("Getting city by slug: {}", slug);

        City city = cityRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Город с slug " + slug + " не найден"));

        return cityMapper.toDto(city);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "city", key = "#id"),
            @CacheEvict(value = "cityBySlug", allEntries = true),
            @CacheEvict(value = "cities", allEntries = true),
            @CacheEvict(value = "popularCities", allEntries = true)
    })
    public void deleteCity(Long id) {
        log.debug("Deleting city with ID: {}", id);

        if (!cityRepository.existsById(id)) {
            throw new ResourceNotFoundException("Город с ID " + id + " не найден");
        }

        cityRepository.deleteById(id);
        log.info("City deleted successfully: ID {}", id);
    }

    @Override
    @Cacheable(value = "cities", key = "#pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<CityResponseDto> getAllCities(Pageable pageable) {
        log.debug("Getting all cities, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        Page<City> cities = cityRepository.findAll(pageable);
        return cities.map(cityMapper::toDto);
    }

    @Override
    @Cacheable(value = "popularCities")
    public List<CityResponseDto> getPopularCities() {
        log.debug("Getting popular cities");

        List<City> cities = cityRepository.findByIsPopularTrue();
        return cityMapper.toDtoList(cities);
    }

    @Override
    public Page<CityResponseDto> getCitiesByPopularity(Boolean isPopular, Pageable pageable) {
        log.debug("Getting cities by popularity: {}", isPopular);

        Page<City> cities = cityRepository.findByIsPopular(isPopular, pageable);
        return cities.map(cityMapper::toDto);
    }

    @Override
    public Page<CityResponseDto> searchCities(String query, Pageable pageable) {
        log.debug("Searching cities with query: {}", query);

        Page<City> cities = cityRepository.search(query, pageable);
        return cities.map(cityMapper::toDto);
    }

    @Override
    public Page<CityResponseDto> getCitiesByCountryCode(String countryCode, Pageable pageable) {
        log.debug("Getting cities by country code: {}", countryCode);

        Page<City> cities = cityRepository.findByCountryCode(countryCode, pageable);
        return cities.map(cityMapper::toDto);
    }

    @Override
    public boolean existsBySlug(String slug) {
        return cityRepository.existsBySlug(slug);
    }

    @Override
    public List<CityResponseDto> getCitiesByIds(List<Long> ids) {
        log.debug("Getting cities by IDs: {}", ids);

        List<City> cities = cityRepository.findAllById(ids);
        return cities.stream()
                .map(cityMapper::toDto)
                .collect(Collectors.toList());
    }
}