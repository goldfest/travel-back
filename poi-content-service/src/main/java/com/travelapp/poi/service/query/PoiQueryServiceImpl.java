package com.travelapp.poi.service.query;

import com.travelapp.poi.client.CityClient;
import com.travelapp.poi.exception.PoiNotFoundException;
import com.travelapp.poi.mapper.PoiMapper;
import com.travelapp.poi.model.dto.request.PoiSearchRequest;
import com.travelapp.poi.model.dto.response.PoiResponse;
import com.travelapp.poi.model.entity.CityExternalDto;
import com.travelapp.poi.model.entity.Poi;
import com.travelapp.poi.model.entity.PoiHours;
import com.travelapp.poi.repository.PoiRepository;
import com.travelapp.poi.repository.PoiTypeRepository;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class PoiQueryServiceImpl implements PoiQueryService {

    private final PoiRepository poiRepository;
    private final PoiMapper poiMapper;
    private final PoiTypeRepository poiTypeRepository;
    private final CityClient cityClient;
    private static final ZoneId FALLBACK_ZONE_ID = ZoneId.of("UTC");

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

    private Specification<Poi> buildSearchSpecification(PoiSearchRequest request) {
        return Specification.where(hasCityId(request.getCityId()))
                .and(isVerified(request.getVerifiedOnly()))
                .and(isNotClosed(request.getExcludeClosed()))
                .and(hasTypeIds(request.getPoiTypeIds()))
                .and(hasQueryTypeHint(request.getSearchQuery(), request.getPoiTypeIds()))
                .and(hasPriceRange(request.getMinPrice(), request.getMaxPrice()))
                .and(hasSearchQuery(request.getSearchQuery()));
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

    private Specification<Poi> hasQueryTypeHint(String searchQuery, List<Long> explicitTypeIds) {
        return (root, query, cb) -> {
            if (explicitTypeIds != null && !explicitTypeIds.isEmpty()) {
                return null;
            }

            Set<String> inferredTypeCodes = inferTypeCodes(searchQuery);
            if (inferredTypeCodes.isEmpty()) {
                return null;
            }

            return root.get("poiType").get("code").in(inferredTypeCodes);
        };
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
            String normalizedQuery = normalizeSearchQuery(searchQuery);
            if (StringUtils.isBlank(normalizedQuery)) return null;
            String like = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("address")), like)
            );
        };
    }

    private String resolveSortProperty(String sortBy) {
        String s = sortBy.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "name" -> "name";
            case "price", "pricelevel" -> "priceLevel";
            default -> "name";
        };
    }

    private PoiResponse enrichPoiResponse(Poi poi) {
        return enrichPoiResponse(poi, null);
    }

    private PoiResponse enrichPoiResponse(Poi poi, Double distanceKm) {
        PoiResponse response = poiMapper.toResponse(poi);

        response.setDistanceKm(distanceKm);

        if (poi.getHours() != null && !poi.getHours().isEmpty()) {
            ZoneId zoneId = resolveZoneId(poi.getCityId());
            response.setIsOpenNow(isOpenNow(poi.getHours(), zoneId));
            response.setCurrentStatus(response.getIsOpenNow() ? "OPEN" : "CLOSED");
        }

        return response;
    }

    private boolean isOpenNow(Set<PoiHours> hours, ZoneId zoneId) {
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        int currentDay = now.getDayOfWeek().getValue() % 7;
        int previousDay = (currentDay + 6) % 7;
        LocalTime currentTime = now.toLocalTime();

        for (PoiHours hour : hours) {
            if (Boolean.TRUE.equals(hour.getAroundTheClock()) && hour.getDayOfWeek().shortValue() == currentDay) {
                return true;
            }

            if (hour.getOpenTime() == null || hour.getCloseTime() == null) {
                continue;
            }

            boolean crossesMidnight = hour.getCloseTime().isBefore(hour.getOpenTime());

            if (hour.getDayOfWeek().shortValue() == currentDay) {
                if (!crossesMidnight
                        && !currentTime.isBefore(hour.getOpenTime())
                        && !currentTime.isAfter(hour.getCloseTime())) {
                    return true;
                }

                if (crossesMidnight && !currentTime.isBefore(hour.getOpenTime())) {
                    return true;
                }
            }

            if (crossesMidnight
                    && hour.getDayOfWeek().shortValue() == previousDay
                    && !currentTime.isAfter(hour.getCloseTime())) {
                return true;
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoiResponse> getPoisBatch(List<Long> ids) {
        return poiRepository.findAllById(ids).stream()
                .map(poiMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoiResponse> searchByCityAndType(Long cityId, String type, Integer limit) {
        int pageSize = (limit == null || limit <= 0) ? 20 : limit;
        PageRequest pageable = PageRequest.of(0, pageSize);

        if (type == null || type.isBlank()) {
            return poiRepository.findByCityIdAndIsVerifiedTrueAndIsClosedFalse(cityId, pageable)
                    .getContent()
                    .stream()
                    .map(this::enrichPoiResponse)
                    .toList();
        }

        var poiType = poiTypeRepository.findByCode(type)
                .orElseThrow(() -> new IllegalArgumentException("POI type not found: " + type));

        return poiRepository.findByCityIdAndPoiTypeIdAndIsVerifiedTrueAndIsClosedFalse(
                        cityId, poiType.getId(), pageable)
                .getContent()
                .stream()
                .map(this::enrichPoiResponse)
                .toList();
    }

    private String normalizeSearchQuery(String searchQuery) {
        if (StringUtils.isBlank(searchQuery)) {
            return null;
        }

        String normalized = searchQuery.toLowerCase(Locale.ROOT);
        for (String keyword : searchableTypeKeywords()) {
            normalized = normalized.replace(keyword, " ");
        }

        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.isBlank() ? null : normalized;
    }

    private Set<String> inferTypeCodes(String searchQuery) {
        if (StringUtils.isBlank(searchQuery)) {
            return Set.of();
        }

        String normalized = searchQuery.toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();

        if (containsAny(normalized, "кафе", "кофейня")) {
            result.add("cafe");
        }
        if (containsAny(normalized, "ресторан", "бар", "паб", "пиццерия", "фастфуд")) {
            result.add("restaurant");
        }
        if (containsAny(normalized, "отель", "гостиница", "хостел")) {
            result.add("hotel");
        }
        if (containsAny(normalized, "парк", "сквер", "сад")) {
            result.add("park");
        }
        if (containsAny(normalized, "музей", "театр", "собор", "храм", "памятник", "достопримечательность")) {
            result.add("landmark");
        }

        return result;
    }

    private List<String> searchableTypeKeywords() {
        return List.of(
                "кафе",
                "кофейня",
                "ресторан",
                "бар",
                "паб",
                "пиццерия",
                "фастфуд",
                "отель",
                "гостиница",
                "хостел",
                "парк",
                "сквер",
                "сад",
                "музей",
                "театр",
                "собор",
                "храм",
                "памятник",
                "достопримечательность"
        );
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private ZoneId resolveZoneId(Long cityId) {
        if (cityId == null) {
            return FALLBACK_ZONE_ID;
        }

        try {
            CityExternalDto city = cityClient.getCityById(cityId);
            if (city != null && StringUtils.isNotBlank(city.getTimeZone())) {
                return ZoneId.of(city.getTimeZone().trim());
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve city timezone for cityId={}: {}", cityId, ex.getMessage());
        }

        return FALLBACK_ZONE_ID;
    }
}