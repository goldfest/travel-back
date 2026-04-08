package com.travelapp.poi.service.command;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
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
        poi.setSlug(request.getSlug());
        poi.setCreatedBy(userId);
        poi.setIsVerified(false);

        applySimpleFields(poi, request, poiType);
        replaceFeatures(poi, request.getFeatures());
        replaceHours(poi, request.getHours());
        replaceMedia(poi, request.getMedia(), userId);
        appendSourcesIfMissing(poi, request.getSources());

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
            replaceFeatures(poi, request.getFeatures());
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
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public void verifyPoiInternal(Long id) {
        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        poi.setIsVerified(true);
        poiRepository.save(poi);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", key = "#id"),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiResponse updatePoiFromImport(Long id, PoiCreateRequest request, Long userId) {
        log.info("Updating POI from import: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        PoiType poiType = poiTypeRepository.findById(request.getPoiTypeId())
                .orElseThrow(() -> new PoiTypeNotFoundException(request.getPoiTypeId()));

        // slug намеренно НЕ меняем при update from import
        applySimpleFields(poi, request, poiType);

        replaceFeatures(poi, request.getFeatures());
        replaceHours(poi, request.getHours());
        replaceMedia(poi, request.getMedia(), userId);
        appendSourcesIfMissing(poi, request.getSources());

        Poi updated = poiRepository.save(poi);
        return poiMapper.toResponse(updated);
    }

    private void applySimpleFields(Poi poi, PoiCreateRequest request, PoiType poiType) {
        poi.setName(request.getName());
        poi.setCityId(request.getCityId());
        poi.setPoiType(poiType);
        poi.setLatitude(request.getLatitude());
        poi.setLongitude(request.getLongitude());
        poi.setAddress(request.getAddress());
        poi.setDescription(request.getDescription());
        poi.setPhone(request.getPhone());
        poi.setSiteUrl(request.getSiteUrl());
        poi.setPriceLevel(request.getPriceLevel());

        if (request.getTags() != null) {
            poi.setTags(request.getTags());
        }
    }

    private void replaceFeatures(Poi poi, Map<String, String> features) {
        poi.getFeatures().clear();

        if (features == null || features.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> e : features.entrySet()) {
            if (StringUtils.isBlank(e.getKey())) {
                continue;
            }

            PoiFeature feature = new PoiFeature();
            feature.setKey(e.getKey().trim());
            feature.setValue(e.getValue());
            poi.addFeature(feature);
        }
    }

    private void replaceHours(Poi poi, List<PoiCreateRequest.HoursRequest> hoursRequests) {
        poi.getHours().clear();

        if (hoursRequests == null || hoursRequests.isEmpty()) {
            return;
        }

        for (PoiCreateRequest.HoursRequest hoursRequest : hoursRequests) {
            if (hoursRequest == null || hoursRequest.getDayOfWeek() == null) {
                continue;
            }

            boolean aroundTheClock = Boolean.TRUE.equals(hoursRequest.getAroundTheClock());
            boolean hasAnyTime = hoursRequest.getOpenTime() != null || hoursRequest.getCloseTime() != null;

            if (!aroundTheClock && !hasAnyTime) {
                continue;
            }

            PoiHours hours = new PoiHours();
            hours.setDayOfWeek(hoursRequest.getDayOfWeek());
            hours.setOpenTime(hoursRequest.getOpenTime());
            hours.setCloseTime(hoursRequest.getCloseTime());
            hours.setAroundTheClock(aroundTheClock);
            poi.addHours(hours);
        }
    }

    private void replaceMedia(Poi poi, List<PoiCreateRequest.MediaRequest> mediaRequests, Long userId) {
        poi.getMedia().clear();

        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        for (PoiCreateRequest.MediaRequest mediaRequest : mediaRequests) {
            if (mediaRequest == null || StringUtils.isBlank(mediaRequest.getUrl())) {
                continue;
            }

            PoiMedia media = new PoiMedia();
            media.setUrl(mediaRequest.getUrl().trim());
            media.setMediaType(mediaRequest.getMediaType());
            media.setUserId(userId);
            poi.addMedia(media);
        }
    }

    private void appendSourcesIfMissing(Poi poi, List<PoiCreateRequest.SourceRequest> sourceRequests) {
        if (sourceRequests == null || sourceRequests.isEmpty()) {
            return;
        }

        for (PoiCreateRequest.SourceRequest sourceRequest : sourceRequests) {
            if (sourceRequest == null
                    || StringUtils.isBlank(sourceRequest.getSourceCode())
                    || StringUtils.isBlank(sourceRequest.getSourceUrl())) {
                continue;
            }

            boolean exists = poi.getSources().stream().anyMatch(existing ->
                    sourceRequest.getSourceCode().equalsIgnoreCase(existing.getSourceCode())
                            && sourceRequest.getSourceUrl().equalsIgnoreCase(existing.getSourceUrl())
            );

            if (exists) {
                continue;
            }

            PoiSource source = new PoiSource();
            source.setSourceCode(sourceRequest.getSourceCode().trim());
            source.setSourceUrl(sourceRequest.getSourceUrl().trim());
            source.setConfidenceScore(sourceRequest.getConfidenceScore());
            poi.addSource(source);
        }
    }

}