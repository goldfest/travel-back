package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.travelapp.poi.client.twogis.TwoGisTextUtils.firstNonBlank;
import static com.travelapp.poi.client.twogis.TwoGisTextUtils.normalizeText;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisResponseParser {

    private final TwoGisTypeResolver typeResolver;
    private final TwoGisRubricExtractor rubricExtractor;
    private final TwoGisFeatureExtractor featureExtractor;
    private final TwoGisHoursExtractor hoursExtractor;
    private final TwoGisContactExtractor contactExtractor;
    private final TwoGisDescriptionBuilder descriptionBuilder;

    public List<TwoGisRawPoiDto> parseResponse(JsonNode response, String requestedType) {
        List<TwoGisRawPoiDto> result = new ArrayList<>();

        JsonNode items = response.path("result").path("items");

        if (items.isArray() && !items.isEmpty()) {
            log.info("2GIS raw response result.items size={}", items.size());
            log.debug("2GIS first item raw: {}", items.get(0).toPrettyString());
        }

        for (JsonNode item : items) {
            String name = item.path("name").asText(null);
            String address = firstNonBlank(
                    normalizeText(item.path("full_address_name").asText(null)),
                    normalizeText(item.path("address_name").asText(null))
            );

            JsonNode point = item.path("point");
            Double lat = point.has("lat") && !point.path("lat").isNull()
                    ? point.path("lat").asDouble()
                    : null;
            Double lon = point.has("lon") && !point.path("lon").isNull()
                    ? point.path("lon").asDouble()
                    : null;

            if (StringUtils.isBlank(name) || lat == null || lon == null) {
                continue;
            }

            String resolvedType = typeResolver.resolvePoiTypeCode(item);

            if (!typeResolver.matchesRequestedType(requestedType, resolvedType)) {
                continue;
            }

            if (!typeResolver.passesStrictTypeFilter(item, requestedType)) {
                continue;
            }

            String externalId = item.path("id").asText(null);
            String sourceUrl = normalizeSourceUrl(item, externalId);
            List<String> rubricNames = rubricExtractor.extractRubricNames(item);
            String purposeName = normalizeText(item.path("purpose_name").asText(null));
            Map<String, String> features = featureExtractor.extractFeatures(item);
            List<TwoGisRawHourDto> hours = hoursExtractor.extractHours(item);

            String description = descriptionBuilder.buildDescription(
                    item,
                    name,
                    purposeName,
                    rubricNames,
                    address,
                    features,
                    resolvedType
            );

            TwoGisRawPoiDto dto = new TwoGisRawPoiDto();
            dto.setExternalId(externalId);
            dto.setName(name);
            dto.setAddress(address);
            dto.setLatitude(lat);
            dto.setLongitude(lon);
            dto.setDescription(description);
            dto.setPhone(contactExtractor.extractContactPhone(item));
            dto.setSiteUrl(contactExtractor.extractSiteUrl(item));
            dto.setPriceLevel(0);
            dto.setPoiTypeCode(resolvedType);
            dto.setSourceUrl(sourceUrl);
            dto.setFeatures(features);
            dto.setHours(hours);
            dto.setPurposeName(purposeName);
            dto.setRubricNames(rubricNames);

            result.add(dto);
        }

        return result;
    }


    private String normalizeSourceUrl(JsonNode item, String externalId) {
        String uri = item.path("uri").asText(null);

        if (StringUtils.isNotBlank(uri)) {
            if (uri.startsWith("http://") || uri.startsWith("https://")) {
                return uri;
            }

            return "https://2gis.ru" + uri;
        }

        if (StringUtils.isNotBlank(externalId)) {
            return "2gis:item:" + externalId;
        }

        return null;
    }
}
