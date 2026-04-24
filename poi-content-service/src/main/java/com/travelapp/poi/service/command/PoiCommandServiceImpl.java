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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

        validateCreateRequest(request);

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
            @CacheEvict(value = "poiCache", allEntries = true),
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

        if (request.getPoiTypeId() != null) {
            PoiType poiType = poiTypeRepository.findById(request.getPoiTypeId())
                    .orElseThrow(() -> new PoiTypeNotFoundException(request.getPoiTypeId()));
            poi.setPoiType(poiType);
        }

        if (request.getHours() != null) {
            replaceHours(poi, request.getHours());
        }

        Poi updated = poiRepository.save(poi);
        return poiMapper.toResponse(updated);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "poiCache", allEntries = true),
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
            @CacheEvict(value = "poiCache", allEntries = true),
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
            @CacheEvict(value = "poiCache", allEntries = true),
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
            @CacheEvict(value = "poiCache", allEntries = true),
            @CacheEvict(value = "pois", allEntries = true)
    })
    public PoiResponse updatePoiFromImport(Long id, PoiCreateRequest request, Long userId) {
        log.info("Updating POI from import: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        PoiType poiType = poiTypeRepository.findById(request.getPoiTypeId())
                .orElseThrow(() -> new PoiTypeNotFoundException(request.getPoiTypeId()));

        validateCreateRequest(request);
        applySimpleFields(poi, request, poiType);

        replaceFeatures(poi, request.getFeatures());
        replaceHours(poi, request.getHours());

        syncSystemMediaFromImport(poi, request.getMedia(), userId);

        appendSourcesIfMissing(poi, request.getSources());

        Poi updated = poiRepository.save(poi);
        return poiMapper.toResponse(updated);
    }

    private void syncSystemMediaFromImport(Poi poi,
                                           List<PoiCreateRequest.MediaRequest> mediaRequests,
                                           Long userId) {
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        Set<String> existingSystemUrls = poi.getMedia().stream()
                .filter(media -> media.getSourceType() == PoiMedia.SourceType.SYSTEM_WIKIMEDIA)
                .map(PoiMedia::getUrl)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        for (PoiCreateRequest.MediaRequest mediaRequest : mediaRequests) {
            if (mediaRequest == null || StringUtils.isBlank(mediaRequest.getUrl())) {
                continue;
            }

            String url = mediaRequest.getUrl().trim();

            if (!isWikimediaMediaUrl(url)) {
                continue;
            }

            if (existingSystemUrls.contains(url)) {
                continue;
            }

            PoiMedia media = new PoiMedia();
            media.setPoi(poi);
            media.setUrl(url);
            media.setMediaType(resolveMediaType(mediaRequest.getMediaType()));
            media.setSourceType(PoiMedia.SourceType.SYSTEM_WIKIMEDIA);
            media.setModerationStatus(PoiMedia.ModerationStatus.APPROVED);
            media.setModeratedBy(userId);
            media.setModeratedAt(java.time.LocalDateTime.now());
            media.setUserId(userId);

            poi.getMedia().add(media);
            existingSystemUrls.add(url);
        }
    }

    private boolean isWikimediaMediaUrl(String url) {
        String normalized = StringUtils.defaultString(url).toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("wikimedia.org") || normalized.contains("wikipedia.org");
    }

    private PoiMedia.MediaType resolveMediaType(PoiMedia.MediaType mediaType) {
        return mediaType != null ? mediaType : PoiMedia.MediaType.PHOTO;
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
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        poi.getMedia().clear();

        for (PoiCreateRequest.MediaRequest mediaRequest : mediaRequests) {
            if (mediaRequest == null || StringUtils.isBlank(mediaRequest.getUrl())) {
                continue;
            }

            String mediaUrl = mediaRequest.getUrl().trim();

            PoiMedia media = new PoiMedia();
            media.setUrl(mediaUrl);
            media.setMediaType(resolveMediaType(mediaRequest.getMediaType()));
            media.setSourceType(resolveImportedMediaSourceType(mediaUrl));
            media.setModerationStatus(PoiMedia.ModerationStatus.APPROVED);
            media.setModeratedBy(userId);
            media.setModeratedAt(java.time.LocalDateTime.now());
            media.setUserId(userId);

            poi.addMedia(media);
        }
    }

    private PoiMedia.SourceType resolveImportedMediaSourceType(String mediaUrl) {
        String normalized = StringUtils.defaultString(mediaUrl).toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("wikimedia.org") || normalized.contains("wikipedia.org")) {
            return PoiMedia.SourceType.SYSTEM_WIKIMEDIA;
        }
        return PoiMedia.SourceType.ADMIN_UPLOAD;
    }

    private void appendSourcesIfMissing(Poi poi, List<PoiCreateRequest.SourceRequest> sourceRequests) {
        if (sourceRequests == null || sourceRequests.isEmpty()) {
            return;
        }

        for (PoiCreateRequest.SourceRequest sourceRequest : sourceRequests) {
            if (sourceRequest == null
                    || StringUtils.isBlank(sourceRequest.getSourceCode())) {
                continue;
            }

            PoiSource existingSource = poi.getSources().stream()
                    .filter(existing -> hasSameSourceIdentity(existing, sourceRequest))
                    .findFirst()
                    .orElse(null);

            if (existingSource != null) {
                if (StringUtils.isBlank(existingSource.getSourceUrl()) && StringUtils.isNotBlank(sourceRequest.getSourceUrl())) {
                    existingSource.setSourceUrl(sourceRequest.getSourceUrl().trim());
                }
                if (StringUtils.isBlank(existingSource.getExternalId()) && StringUtils.isNotBlank(sourceRequest.getExternalId())) {
                    existingSource.setExternalId(sourceRequest.getExternalId().trim());
                }
                if (sourceRequest.getConfidenceScore() != null) {
                    existingSource.setConfidenceScore(sourceRequest.getConfidenceScore());
                }
                continue;
            }

            PoiSource source = new PoiSource();
            source.setSourceCode(sourceRequest.getSourceCode().trim());
            source.setSourceUrl(StringUtils.trimToNull(sourceRequest.getSourceUrl()));
            source.setExternalId(StringUtils.trimToNull(sourceRequest.getExternalId()));
            source.setConfidenceScore(sourceRequest.getConfidenceScore());
            poi.addSource(source);
        }
    }

    private boolean hasSameSourceIdentity(PoiSource existing, PoiCreateRequest.SourceRequest incoming) {
        if (existing == null || incoming == null) {
            return false;
        }

        if (!StringUtils.equalsIgnoreCase(existing.getSourceCode(), incoming.getSourceCode())) {
            return false;
        }

        if (StringUtils.isNotBlank(existing.getExternalId()) && StringUtils.isNotBlank(incoming.getExternalId())) {
            return StringUtils.equalsIgnoreCase(existing.getExternalId(), incoming.getExternalId());
        }

        if (StringUtils.isNotBlank(existing.getSourceUrl()) && StringUtils.isNotBlank(incoming.getSourceUrl())) {
            return StringUtils.equalsIgnoreCase(existing.getSourceUrl(), incoming.getSourceUrl());
        }

        return false;
    }

    private void validateCreateRequest(PoiCreateRequest request) {
        if (request == null) {
            throw new ValidationException("POI request must not be null");
        }

        validateDescription(request.getDescription());
        validateUrl(request.getSiteUrl(), "Site URL");

        if (request.getMedia() != null) {
            for (PoiCreateRequest.MediaRequest media : request.getMedia()) {
                if (media == null) {
                    continue;
                }
                validateUrl(media.getUrl(), "Media URL");
            }
        }
    }

    private void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            return;
        }

        String normalized = description.trim();

        if (normalized.length() < 20) {
            throw new ValidationException("Description is too short");
        }

        if ("Описание объекта временно отсутствует.".equalsIgnoreCase(normalized)) {
            throw new ValidationException("Description is placeholder-only");
        }
    }

    private void validateUrl(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return;
        }

        String normalized = value.trim().toLowerCase();
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new ValidationException(fieldName + " must start with http:// or https://");
        }
    }

}