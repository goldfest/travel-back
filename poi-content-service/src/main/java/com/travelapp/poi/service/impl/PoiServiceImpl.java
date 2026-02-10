package com.travelapp.poi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.davidmoten.geo.GeoHash;
import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.exception.PoiTypeNotFoundException;
import com.travelapp.poi.exception.ValidationException;
import com.travelapp.poi.model.dto.request.PoiCreateRequest;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.request.PoiUpdateRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.*;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiTypeRepository;
import com.travelapp.poi.service.PoiService;
import com.travelapp.poi.service.mapper.PoiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiServiceImpl implements PoiService {

    private final PoiRepository poiRepository;
    private final PoiTypeRepository poiTypeRepository;
    private final PoiMapper poiMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    @CacheEvict(value = {"pois", "poiCache"}, allEntries = true)
    public PoiResponse createPoi(PoiCreateRequest request, Long userId) {
        log.info("Creating new POI: {} by user {}", request.getName(), userId);

        // Validate POI type
        PoiType poiType = poiTypeRepository.findById(request.getPoiTypeId())
                .orElseThrow(() -> new PoiTypeNotFoundException(request.getPoiTypeId()));

        // Check if slug already exists
        if (poiRepository.findBySlug(request.getSlug()).isPresent()) {
            throw new ValidationException("Slug already exists: " + request.getSlug());
        }

        // Create POI entity
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
        poi.setIsVerified(false); // New POIs need verification

        // Set tags
        if (request.getTags() != null) {
            poi.setTags(request.getTags());
        }

        // Save POI first
        poi = poiRepository.save(poi);

        // Add features
        if (request.getFeatures() != null) {
            request.getFeatures().forEach((key, value) -> {
                PoiFeature feature = new PoiFeature();
                feature.setKey(key);
                feature.setValue(value);
                poi.addFeature(feature);
            });
        }

        // Add hours
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

        // Add media
        if (request.getMedia() != null) {
            request.getMedia().forEach(mediaRequest -> {
                PoiMedia media = new PoiMedia();
                media.setUrl(mediaRequest.getUrl());
                media.setMediaType(mediaRequest.getMediaType());
                media.setUserId(userId);
                poi.addMedia(media);
            });
        }

        // Add sources
        if (request.getSources() != null) {
            request.getSources().forEach(sourceRequest -> {
                PoiSource source = new PoiSource();
                source.setSourceCode(sourceRequest.getSourceCode());
                source.setSourceUrl(sourceRequest.getSourceUrl());
                source.setConfidenceScore(sourceRequest.getConfidenceScore());
                poi.addSource(source);
            });
        }

        log.info("POI created successfully with ID: {}", poi.getId());
        return poiMapper.toResponse(poi);
    }

    @Override
    @Cacheable(value = "poiCache", key = "#id")
    @Transactional(readOnly = true)
    public PoiResponse getPoiById(Long id) {
        log.debug("Fetching POI by ID: {}", id);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        return enrichPoiResponse(poi);
    }

    @Override
    @Cacheable(value = "poiCache", key = "#slug")
    @Transactional(readOnly = true)
    public PoiResponse getPoiBySlug(String slug) {
        log.debug("Fetching POI by slug: {}", slug);

        Poi poi = poiRepository.findBySlug(slug)
                .orElseThrow(() -> new PoiNotFoundException("Slug: " + slug));

        return enrichPoiResponse(poi);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiCache", "pois"}, key = "#id")
    public PoiResponse updatePoi(Long id, PoiUpdateRequest request, Long userId) {
        log.info("Updating POI: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        // Check slug uniqueness
        if (StringUtils.isNotBlank(request.getSlug()) &&
                !request.getSlug().equals(poi.getSlug()) &&
                poiRepository.existsBySlugAndIdNot(request.getSlug(), id)) {
            throw new ValidationException("Slug already exists: " + request.getSlug());
        }

        // Update fields
        if (StringUtils.isNotBlank(request.getName())) {
            poi.setName(request.getName());
        }
        if (StringUtils.isNotBlank(request.getSlug())) {
            poi.setSlug(request.getSlug());
        }
        if (request.getLatitude() != null) {
            poi.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            poi.setLongitude(request.getLongitude());
        }
        if (request.getAddress() != null) {
            poi.setAddress(request.getAddress());
        }
        if (request.getDescription() != null) {
            poi.setDescription(request.getDescription());
        }
        if (request.getPhone() != null) {
            poi.setPhone(request.getPhone());
        }
        if (request.getSiteUrl() != null) {
            poi.setSiteUrl(request.getSiteUrl());
        }
        if (request.getPriceLevel() != null) {
            poi.setPriceLevel(request.getPriceLevel());
        }
        if (request.getIsVerified() != null && isAdminUser(userId)) {
            poi.setIsVerified(request.getIsVerified());
        }
        if (request.getIsClosed() != null) {
            poi.setIsClosed(request.getIsClosed());
        }
        if (request.getTags() != null) {
            poi.setTags(request.getTags());
        }

        // Update features
        if (request.getFeatures() != null) {
            // Clear existing features
            poi.getFeatures().clear();

            // Add new features
            request.getFeatures().forEach((key, value) -> {
                PoiFeature feature = new PoiFeature();
                feature.setKey(key);
                feature.setValue(value);
                poi.addFeature(feature);
            });
        }

        poi = poiRepository.save(poi);
        log.info("POI updated successfully: {}", id);

        return poiMapper.toResponse(poi);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiCache", "pois"}, key = "#id")
    public void deletePoi(Long id, Long userId) {
        log.info("Deleting POI: {} by user {}", id, userId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        // Check permissions (only admin or creator can delete)
        if (!isAdminUser(userId) && !poi.getCreatedBy().equals(userId)) {
            throw new ValidationException("You don't have permission to delete this POI");
        }

        poiRepository.delete(poi);
        log.info("POI deleted successfully: {}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiCache", "pois"}, key = "#id")
    public void verifyPoi(Long id, Long adminId) {
        log.info("Verifying POI: {} by admin {}", id, adminId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        poi.setIsVerified(true);
        poiRepository.save(poi);

        log.info("POI verified: {}", id);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"poiCache", "pois"}, key = "#id")
    public void unverifyPoi(Long id, Long adminId) {
        log.info("Unverifying POI: {} by admin {}", id, adminId);

        Poi poi = poiRepository.findById(id)
                .orElseThrow(() -> new PoiNotFoundException(id));

        poi.setIsVerified(false);
        poiRepository.save(poi);

        log.info("POI unverified: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> searchPois(PoiSearchRequest request) {
        log.debug("Searching POIs with filters: {}", request);

        // Build specification based on filters
        Specification<Poi> spec = buildSearchSpecification(request);

        Pageable pageable = Pageable.ofSize(request.getSize())
                .withPage(request.getPage() - 1)
                .withSort(request.getSortDirection(), request.getSortBy());

        Page<Poi> poiPage = poiRepository.findAll(spec, pageable);

        return poiPage.map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "pois", key = "{#cityId, #userLat, #userLng, #radiusKm, #limit}")
    public List<PoiResponse> getNearbyPois(Long cityId, BigDecimal userLat, BigDecimal userLng,
                                           Integer radiusKm, Integer limit) {
        log.debug("Finding nearby POIs for city: {}, location: ({}, {})",
                cityId, userLat, userLng);

        List<Poi> pois = poiRepository.findNearby(cityId, userLat, userLng, radiusKm);

        if (limit != null && limit > 0 && pois.size() > limit) {
            pois = pois.subList(0, limit);
        }

        return pois.stream()
                .map(this::enrichPoiResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "pois", key = "{#cityId, #pageable}")
    public Page<PoiResponse> getPoisByCity(Long cityId, Pageable pageable) {
        log.debug("Fetching POIs for city: {}", cityId);

        Page<Poi> poiPage = poiRepository.findByCityIdAndIsVerifiedTrue(cityId, pageable);

        return poiPage.map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "pois", key = "{#poiTypeId, #pageable}")
    public Page<PoiResponse> getPoisByType(Long poiTypeId, Pageable pageable) {
        log.debug("Fetching POIs by type: {}", poiTypeId);

        Page<Poi> poiPage = poiRepository.findByCityIdAndPoiTypeId(1L, poiTypeId, pageable);

        return poiPage.map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> getUnverifiedPois(Pageable pageable) {
        log.debug("Fetching unverified POIs");

        Page<Poi> poiPage = poiRepository.findUnverified(pageable);

        return poiPage.map(poiMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPoiCountByCity(Long cityId) {
        return poiRepository.countByCityId(cityId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getPoiCountByType(Long poiTypeId) {
        return poiRepository.countByPoiTypeId(poiTypeId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "poiCache", key = "#poiId")
    public void updateRating(Long poiId, BigDecimal newRating) {
        log.debug("Updating rating for POI: {}", poiId);

        Poi poi = poiRepository.findById(poiId)
                .orElseThrow(() -> new PoiNotFoundException(poiId));

        poi.updateRating(newRating);
        poiRepository.save(poi);
    }

    // Helper methods
    private Specification<Poi> buildSearchSpecification(PoiSearchRequest request) {
        return Specification.where(hasCityId(request.getCityId()))
                .and(isVerified(request.getVerifiedOnly()))
                .and(isNotClosed(request.getExcludeClosed()))
                .and(hasTypeIds(request.getPoiTypeIds()))
                .and(hasMinRating(request.getMinRating()))
                .and(hasPriceRange(request.getMinPrice(), request.getMaxPrice()))
                .and(hasSearchQuery(request.getSearchQuery()))
                .and(hasFeatures(request.getRequiredFeatures()));
    }

    private Specification<Poi> hasCityId(Long cityId) {
        return (root, query, cb) -> cityId == null ? null : cb.equal(root.get("cityId"), cityId);
    }

    private Specification<Poi> isVerified(Boolean verifiedOnly) {
        return (root, query, cb) ->
                verifiedOnly == null || !verifiedOnly ? null : cb.isTrue(root.get("isVerified"));
    }

    private Specification<Poi> isNotClosed(Boolean excludeClosed) {
        return (root, query, cb) ->
                excludeClosed == null || !excludeClosed ? null : cb.isFalse(root.get("isClosed"));
    }

    private Specification<Poi> hasTypeIds(List<Long> typeIds) {
        return (root, query, cb) ->
                typeIds == null || typeIds.isEmpty() ? null : root.get("poiType").get("id").in(typeIds);
    }

    private Specification<Poi> hasMinRating(BigDecimal minRating) {
        return (root, query, cb) ->
                minRating == null ? null : cb.greaterThanOrEqualTo(root.get("averageRating"), minRating);
    }

    private Specification<Poi> hasPriceRange(Short minPrice, Short maxPrice) {
        return (root, query, cb) -> {
            if (minPrice == null && maxPrice == null) return null;

            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceLevel"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceLevel"), maxPrice));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<Poi> hasSearchQuery(String searchQuery) {
        return (root, query, cb) -> {
            if (StringUtils.isBlank(searchQuery)) return null;

            String likePattern = "%" + searchQuery.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), likePattern),
                    cb.like(cb.lower(root.get("description")), likePattern),
                    cb.like(cb.lower(root.get("address")), likePattern)
            );
        };
    }

    private Specification<Poi> hasFeatures(List<String> requiredFeatures) {
        return (root, query, cb) -> {
            if (requiredFeatures == null || requiredFeatures.isEmpty()) return null;

            // This is a simplified implementation
            // For complex feature queries, consider using subqueries
            return cb.conjunction();
        };
    }

    private PoiResponse enrichPoiResponse(Poi poi) {
        PoiResponse response = poiMapper.toResponse(poi);

        // Calculate if POI is open now
        if (poi.getHours() != null && !poi.getHours().isEmpty()) {
            response.setIsOpenNow(isOpenNow(poi.getHours()));
            response.setCurrentStatus(getCurrentStatus(poi.getHours()));
        }

        return response;
    }

    private boolean isOpenNow(Set<PoiHours> hours) {
        int currentDay = java.time.LocalDate.now().getDayOfWeek().getValue() % 7; // 0-6 (0 = Sunday)
        LocalTime currentTime = LocalTime.now();

        for (PoiHours hour : hours) {
            if (hour.getDayOfWeek().shortValue() == currentDay) {
                if (Boolean.TRUE.equals(hour.getAroundTheClock())) {
                    return true;
                }
                if (hour.getOpenTime() != null && hour.getCloseTime() != null) {
                    return !currentTime.isBefore(hour.getOpenTime()) &&
                            !currentTime.isAfter(hour.getCloseTime());
                }
            }
        }
        return false;
    }

    private String getCurrentStatus(Set<PoiHours> hours) {
        return isOpenNow(hours) ? "OPEN" : "CLOSED";
    }

    private boolean isAdminUser(Long userId) {
        // This should call auth service to check user role
        // For now, implement a simple check
        return userId != null && userId.toString().startsWith("admin");
    }
}