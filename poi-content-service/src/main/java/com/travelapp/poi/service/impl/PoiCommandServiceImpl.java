package com.travelapp.poi.service.impl;

import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.mapper.PoiMapper;
import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.*;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiTypeRepository;
import com.travelapp.poi.security.SecurityUtils;
import com.travelapp.poi.service.command.PoiCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiCommandServiceImpl implements PoiCommandService {

    private final PoiRepository poiRepository;
    private final PoiTypeRepository poiTypeRepository;
    private final PoiMapper poiMapper;

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiResponse createPoi(PoiCreateRequest request, Long userId) {
        log.info("Creating new POI: {} by user {}", request.getName(), userId);

        PoiType poiType = poiTypeRepository.findById(request.getPoiTypeId())
                .orElseThrow(() -> new PoiTypeNotFoundException(request.getPoiTypeId()));

        if (poiRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new ValidationException("Slug already exists: " + request.getSlug());
        }

        Poi poi = new Poi();
        poi.setName(request.getName());
        poi.setSlug(request.getSlug());
        poi.setCityId(request.getCityId());
        poi.setPoiType(poiType);
        poi.setLatitude(request.getLatitude());
        poi.setLongitude(request.getLongitude());
        poi.setAddress(request.getAddress());
        poi.setDescription(request.getDescription());
        poi.setPhone(request.getPhone());
        poi.setSiteUrl(request.getSiteUrl());
        poi.setPriceLevel(request.getPriceLevel());
        poi.setCreatedBy(userId);
        poi.setIsVerified(false);

        if (request.getTags() != null) {
            poi.setTags(request.getTags());
        }

        if (request.getFeatures() != null) {
            for (Map.Entry<String, String> e : request.getFeatures().entrySet()) {
                PoiFeature feature = new PoiFeature();
                feature.setKey(e.getKey());
                feature.setValue(e.getValue());
                poi.addFeature(feature);
            }
        }

        if (request.getHours() != null) {
            request.getHours().forEach(hoursRequest -> {
                PoiHours hours = new PoiHours();
                hours.setDayOfWeek(hoursRequest.getDayOfWeek());
                hours.setOpenTime(hoursRequest.getOpenTime());
                hours.setCloseTime(hoursRequest.getCloseTime());
                hours.setAroundTheClock(hoursRequest.getAroundTheClock());
                poi.addHours(hours);
            });
        }

        if (request.getMedia() != null) {
            request.getMedia().forEach(mediaRequest -> {
                PoiMedia media = new PoiMedia();
                media.setUrl(mediaRequest.getUrl());
                media.setMediaType(mediaRequest.getMediaType());
                media.setUserId(userId);
                poi.addMedia(media);
            });
        }

        if (request.getSources() != null) {
            request.getSources().forEach(sourceRequest -> {
                PoiSource source = new PoiSource();
                source.setSourceCode(sourceRequest.getSourceCode());
                source.setSourceUrl(sourceRequest.getSourceUrl());
                source.setConfidenceScore(sourceRequest.getConfidenceScore());
                poi.addSource(source);
            });
        }

        Poi saved = poiRepository.save(poi);
        log.info("POI created successfully with ID: {}", saved.getId());
        return poiMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId) {
        log.info("Updating POI: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        if (StringUtils.isNotBlank(request.getSlug())
                && !request.getSlug().equals(poi.getSlug())
                && poiRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new ValidationException("Slug already exists: " + request.getSlug());
        }

        if (StringUtils.isNotBlank(request.getName())) poi.setName(request.getName());
        if (StringUtils.isNotBlank(request.getSlug())) poi.setSlug(request.getSlug());
        if (request.getLatitude() != null) poi.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) poi.setLongitude(request.getLongitude());
        if (request.getAddress() != null) poi.setAddress(request.getAddress());
        if (request.getDescription() != null) poi.setDescription(request.getDescription());
        if (request.getPhone() != null) poi.setPhone(request.getPhone());
        if (request.getSiteUrl() != null) poi.setSiteUrl(request.getSiteUrl());
        if (request.getPriceLevel() != null) poi.setPriceLevel(request.getPriceLevel());
        if (request.getIsClosed() != null) poi.setIsClosed(request.getIsClosed());
        if (request.getTags() != null) poi.setTags(request.getTags());

        // Менять isVerified можно только админу
        if (request.getIsVerified() != null) {
            if (!SecurityUtils.hasRole("ADMIN")) {
                throw new ValidationException("Only admin can change verification status");
            }
            poi.setIsVerified(request.getIsVerified());
        }

        if (request.getFeatures() != null) {
            poi.getFeatures().clear();
            for (Map.Entry<String, String> e : request.getFeatures().entrySet()) {
                PoiFeature feature = new PoiFeature();
                feature.setKey(e.getKey());
                feature.setValue(e.getValue());
                poi.addFeature(feature);
            }
        }

        Poi updated = poiRepository.save(poi);
        return poiMapper.toResponse(updated);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void deletePoi(Long id, Long userId) {
        log.info("Deleting POI: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        boolean isAdmin = SecurityUtils.hasRole("ADMIN");
        boolean isOwner = poi.getCreatedBy() != null && poi.getCreatedBy().equals(userId);

        if (!isAdmin && !isOwner) {
            throw new ValidationException("You don't have permission to delete this POI");
        }

        poiRepository.delete(poi);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void verifyPoi(Long id, Long adminId) {
        // на всякий случай защита и здесь тоже
        SecurityUtils.requireAdmin();

        Poi poi = poiRepository.findById(id).orElseThrow(() -> new PoiNotFoundException(id));
        poi.setIsVerified(true);
        poiRepository.save(poi);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void unverifyPoi(Long id, Long adminId) {
        SecurityUtils.requireAdmin();

        Poi poi = poiRepository.findById(id).orElseThrow(() -> new PoiNotFoundException(id));
        poi.setIsVerified(false);
        poiRepository.save(poi);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#poiId"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void updateRating(Long poiId, BigDecimal newRating) {
        Poi poi = poiRepository.findById(poiId).orElseThrow(() -> new PoiNotFoundException(poiId));
        poi.updateRating(newRating);
        poiRepository.save(poi);
    }
}