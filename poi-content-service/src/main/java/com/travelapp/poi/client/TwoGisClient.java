package com.travelapp.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.client.twogis.SearchPoint;
import com.travelapp.poi.client.twogis.TwoGisApiClient;
import com.travelapp.poi.client.twogis.TwoGisGridBuilder;
import com.travelapp.poi.client.twogis.TwoGisResponseParser;
import com.travelapp.poi.client.twogis.TwoGisTypeResolver;
import com.travelapp.poi.config.TwoGisProperties;
import com.travelapp.poi.model.entity.CityExternalDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisClient {

    private static final int DEFAULT_GRID_RADIUS_KM = 12;
    private static final int DEFAULT_CELL_STEP_KM = 4;
    private static final int DEFAULT_SEARCH_RADIUS_METERS = 2500;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int DEFAULT_MAX_PAGES = 20;
    private static final int MAX_2GIS_PAGE_SIZE = 10;
    private static final int DUPLICATE_ONLY_PAGES_BREAK_THRESHOLD = 2;
    private static final int DUPLICATE_ONLY_POINTS_BREAK_THRESHOLD = 5;

    private final TwoGisProperties properties;
    private final CityClient cityClient;
    private final TwoGisApiClient twoGisApiClient;
    private final TwoGisGridBuilder twoGisGridBuilder;
    private final TwoGisResponseParser twoGisResponseParser;
    private final TwoGisTypeResolver twoGisTypeResolver;

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS API for query='{}', cityId={}", query, cityId);

        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("2GIS query must not be blank");
        }

        if (cityId == null) {
            throw new IllegalArgumentException("City ID is required for grid search");
        }

        CityExternalDto city = resolveCity(cityId);
        if (city == null || city.getCenterLat() == null || city.getCenterLng() == null) {
            throw new IllegalStateException("City center coordinates are required for grid search");
        }

        String cityName = StringUtils.trimToNull(city.getName());
        String requestedType = twoGisTypeResolver.inferRequestedPoiType(query);

        int gridRadiusKm = safePositive(properties.getGridRadiusKm(), DEFAULT_GRID_RADIUS_KM);
        int cellStepKm = safePositive(properties.getCellStepKm(), DEFAULT_CELL_STEP_KM);
        int radiusMeters = safePositive(properties.getSearchRadiusMeters(), DEFAULT_SEARCH_RADIUS_METERS);
        int pageSize = safePageSize(properties.getPageSize());
        int maxPages = safePositive(properties.getMaxPages(), DEFAULT_MAX_PAGES);

        List<SearchPoint> searchPoints = twoGisGridBuilder.buildGridPoints(
                city.getCenterLat(),
                city.getCenterLng(),
                gridRadiusKm,
                cellStepKm
        );

        int estimatedMaxRequests = searchPoints.size() * maxPages;

        log.info(
                "2GIS grid search context: query='{}', cityId={}, cityName='{}', requestedType={}, points={}, pageSize={}, maxPages={}, radiusMeters={}, estimatedMaxRequests={}",
                query,
                cityId,
                cityName,
                requestedType,
                searchPoints.size(),
                pageSize,
                maxPages,
                radiusMeters,
                estimatedMaxRequests
        );

        List<TwoGisRawPoiDto> aggregated = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        int duplicateOnlyPointsInRow = 0;
        int pointIndex = 0;

        for (SearchPoint point : searchPoints) {
            pointIndex++;

            log.info(
                    "2GIS grid point {}/{}: lat={}, lng={}, distanceKm={}",
                    pointIndex,
                    searchPoints.size(),
                    point.lat(),
                    point.lng(),
                    point.distanceKm()
            );

            int duplicateOnlyPagesInRow = 0;
            int aggregatedBeforePoint = aggregated.size();

            for (int page = 1; page <= maxPages; page++) {
                JsonNode response = twoGisApiClient.fetchPage(
                        query,
                        point.lng(),
                        point.lat(),
                        radiusMeters,
                        page,
                        pageSize
                );

                if (twoGisApiClient.hasLogicalApiError(response)) {
                    log.warn(
                            "2GIS logical API error on point {}/{} page {}. Response={}",
                            pointIndex,
                            searchPoints.size(),
                            page,
                            response != null ? response.toPrettyString() : null
                    );
                    break;
                }

                JsonNode items = response.path("result").path("items");
                int pageItems = items.isArray() ? items.size() : 0;
                int total = response.path("result").path("total").asInt(-1);

                log.info(
                        "2GIS point {}/{} page {} fetched: pageItems={}, reportedTotal={}",
                        pointIndex,
                        searchPoints.size(),
                        page,
                        pageItems,
                        total
                );

                if (pageItems == 0) {
                    break;
                }

                List<TwoGisRawPoiDto> parsedPage = twoGisResponseParser.parseResponse(response, requestedType);
                int addedOnPage = addUniquePois(parsedPage, aggregated, seenIds);

                log.info(
                        "2GIS point {}/{} page {} parsed: acceptedOnPage={}, aggregated={}",
                        pointIndex,
                        searchPoints.size(),
                        page,
                        addedOnPage,
                        aggregated.size()
                );

                if (addedOnPage == 0) {
                    duplicateOnlyPagesInRow++;
                } else {
                    duplicateOnlyPagesInRow = 0;
                }

                if (duplicateOnlyPagesInRow >= DUPLICATE_ONLY_PAGES_BREAK_THRESHOLD) {
                    log.info(
                            "Breaking pagination for point {}/{} because {} duplicate-only pages were received in a row",
                            pointIndex,
                            searchPoints.size(),
                            duplicateOnlyPagesInRow
                    );
                    break;
                }

                if (pageItems < pageSize) {
                    break;
                }
            }

            int addedOnPoint = aggregated.size() - aggregatedBeforePoint;

            if (addedOnPoint == 0) {
                duplicateOnlyPointsInRow++;
            } else {
                duplicateOnlyPointsInRow = 0;
            }

            if (duplicateOnlyPointsInRow >= DUPLICATE_ONLY_POINTS_BREAK_THRESHOLD) {
                log.info(
                        "Breaking grid search because {} points in a row produced no new POIs",
                        duplicateOnlyPointsInRow
                );
                break;
            }
        }

        log.info(
                "2GIS grid search completed: aggregatedResults={}, query='{}', cityId={}",
                aggregated.size(),
                query,
                cityId
        );

        return aggregated;
    }

    private int addUniquePois(List<TwoGisRawPoiDto> parsedPage,
                              List<TwoGisRawPoiDto> aggregated,
                              Set<String> seenIds) {
        int addedOnPage = 0;

        for (TwoGisRawPoiDto dto : parsedPage) {
            String dedupeKey = buildDedupeKey(dto);

            if (dedupeKey != null && !seenIds.add(dedupeKey)) {
                continue;
            }

            aggregated.add(dto);
            addedOnPage++;
        }

        return addedOnPage;
    }

    private CityExternalDto resolveCity(Long cityId) {
        try {
            return cityClient.getCityById(cityId);
        } catch (Exception ex) {
            log.warn("Failed to resolve city metadata for cityId={}: {}", cityId, ex.getMessage());
            return null;
        }
    }

    private String buildDedupeKey(TwoGisRawPoiDto dto) {
        if (StringUtils.isNotBlank(dto.getExternalId())) {
            return "ext:" + dto.getExternalId().trim();
        }

        if (StringUtils.isNotBlank(dto.getSourceUrl())) {
            return "src:" + dto.getSourceUrl().trim();
        }

        if (StringUtils.isNotBlank(dto.getName()) && StringUtils.isNotBlank(dto.getAddress())) {
            return "nameaddr:" + dto.getName().trim() + "|" + dto.getAddress().trim();
        }

        return null;
    }

    private int safePageSize(Integer value) {
        if (value == null || value <= 0) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(value, MAX_2GIS_PAGE_SIZE);
    }

    private int safePositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}