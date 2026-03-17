package com.travelapp.poi.service.query;

import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.mapper.PoiMapper;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.model.entity.PoiHours;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.service.NearbyPoiProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoiQueryServiceImpl implements PoiQueryService {

    private final PoiRepository poiRepository;
    private final PoiMapper poiMapper;

    @Override
    @Cacheable(value = "poiCache", key = "#id")
    @Transactional(readOnly = true)
    public PoiResponse getPoiById(Long id) {
        Poi poi = poiRepository.findById(id).orElseThrow(() -> new PoiNotFoundException(id));
        return enrichPoiResponse(poi);
    }

    @Override
    @Cacheable(value = "poiCache", key = "'slug:' + #slug")
    @Transactional(readOnly = true)
    public PoiResponse getPoiBySlug(String slug) {
        Poi poi = poiRepository.findBySlug(slug)
                .orElseThrow(() -> new PoiNotFoundException("Slug: " + slug));
        return enrichPoiResponse(poi);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> searchPois(PoiSearchRequest request) {
        Specification<Poi> spec = buildSearchSpecification(request);

        Sort sort = Sort.unsorted();
        if (request.getSortDirection() != null && StringUtils.isNotBlank(request.getSortBy())) {
            sort = Sort.by(request.getSortDirection(), resolveSortProperty(request.getSortBy()));
        }

        Pageable pageable = PageRequest.of(request.getPage() - 1, request.getSize(), sort);
        return poiRepository.findAll(spec, pageable).map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "pois", key = "{#cityId, #userLat, #userLng, #radiusKm, #limit}")
    public List<PoiResponse> getNearbyPois(Long cityId, BigDecimal userLat, BigDecimal userLng,
                                           Integer radiusKm, Integer limit) {

        List<NearbyPoiProjection> nearby = poiRepository.findNearbyWithDistance(cityId, userLat, userLng, radiusKm);

        if (limit != null && limit > 0 && nearby.size() > limit) {
            nearby = nearby.subList(0, limit);
        }

        List<Long> poiIds = nearby.stream()
                .map(NearbyPoiProjection::getId)
                .toList();

        if (poiIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Double> distanceMap = nearby.stream()
                .collect(Collectors.toMap(NearbyPoiProjection::getId, NearbyPoiProjection::getDistanceKm));

        Map<Long, Poi> poiMap = poiRepository.findAllById(poiIds).stream()
                .collect(Collectors.toMap(Poi::getId, Function.identity()));

        return poiIds.stream()
                .map(poiMap::get)
                .filter(Objects::nonNull)
                .map(poi -> enrichPoiResponse(poi, distanceMap.get(poi.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> getPoisByCity(Long cityId, Pageable pageable) {
        return poiRepository.findByCityIdAndIsVerifiedTrue(cityId, pageable).map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> getPoisByType(Long poiTypeId, Pageable pageable) {
        return poiRepository.findByPoiTypeIdAndIsVerifiedTrue(poiTypeId, pageable).map(this::enrichPoiResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoiResponse> getUnverifiedPois(Pageable pageable) {
        return poiRepository.findUnverified(pageable).map(poiMapper::toResponse);
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

    // ---- specification ----

    private Specification<Poi> buildSearchSpecification(PoiSearchRequest request) {
        return Specification.where(hasCityId(request.getCityId()))
                .and(isVerified(request.getVerifiedOnly()))
                .and(isNotClosed(request.getExcludeClosed()))
                .and(hasTypeIds(request.getPoiTypeIds()))
                .and(hasPriceRange(request.getMinPrice(), request.getMaxPrice()))
                .and(hasSearchQuery(request.getSearchQuery()));
        // features сейчас заглушка у тебя — оставил как есть, лучше доделать через join/subquery
    }

    private Specification<Poi> hasCityId(Long cityId) {
        return (root, query, cb) -> cityId == null ? null : cb.equal(root.get("cityId"), cityId);
    }

    private Specification<Poi> isVerified(Boolean verifiedOnly) {
        return (root, query, cb) -> verifiedOnly == null || !verifiedOnly ? null : cb.isTrue(root.get("isVerified"));
    }

    private Specification<Poi> isNotClosed(Boolean excludeClosed) {
        return (root, query, cb) -> excludeClosed == null || !excludeClosed ? null : cb.isFalse(root.get("isClosed"));
    }

    private Specification<Poi> hasTypeIds(List<Long> typeIds) {
        return (root, query, cb) -> typeIds == null || typeIds.isEmpty() ? null : root.get("poiType").get("id").in(typeIds);
    }

    private Specification<Poi> hasPriceRange(Short minPrice, Short maxPrice) {
        return (root, query, cb) -> {
            if (minPrice == null && maxPrice == null) return null;
            List<jakarta.persistence.criteria.Predicate> preds = new ArrayList<>();
            if (minPrice != null) preds.add(cb.greaterThanOrEqualTo(root.get("priceLevel"), minPrice));
            if (maxPrice != null) preds.add(cb.lessThanOrEqualTo(root.get("priceLevel"), maxPrice));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<Poi> hasSearchQuery(String searchQuery) {
        return (root, query, cb) -> {
            if (StringUtils.isBlank(searchQuery)) return null;
            String like = "%" + searchQuery.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("address")), like)
            );
        };
    }

    private String resolveSortProperty(String sortBy) {
        // whitelist: не даем сортировать по произвольному полю
        String s = sortBy.trim().toLowerCase();
        return switch (s) {
            case "name" -> "name";
            case "price", "pricelevel" -> "priceLevel";
            default -> "name";
        };
    }

    // ---- enrich ----

    private PoiResponse enrichPoiResponse(Poi poi) {
        return enrichPoiResponse(poi, null);
    }

    private PoiResponse enrichPoiResponse(Poi poi, Double distanceKm) {
        PoiResponse response = poiMapper.toResponse(poi);

        response.setDistanceKm(distanceKm);

        if (poi.getHours() != null && !poi.getHours().isEmpty()) {
            response.setIsOpenNow(isOpenNow(poi.getHours()));
            response.setCurrentStatus(response.getIsOpenNow() ? "OPEN" : "CLOSED");
        }

        return response;
    }

    private boolean isOpenNow(Set<PoiHours> hours) {
        int currentDay = java.time.LocalDate.now().getDayOfWeek().getValue() % 7; // 0-6 (0=Sunday)
        LocalTime currentTime = LocalTime.now();

        for (PoiHours hour : hours) {
            if (hour.getDayOfWeek().shortValue() == currentDay) {
                if (Boolean.TRUE.equals(hour.getAroundTheClock())) return true;
                if (hour.getOpenTime() != null && hour.getCloseTime() != null) {
                    return !currentTime.isBefore(hour.getOpenTime()) && !currentTime.isAfter(hour.getCloseTime());
                }
            }
        }
        return false;
    }
}