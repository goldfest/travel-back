package com.travelapp.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.config.TwoGisProperties;
import com.travelapp.poi.model.entity.CityExternalDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawMediaDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

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
    private static final int DUPLICATE_ONLY_POINTS_BREAK_THRESHOLD = 2;

    private final TwoGisProperties properties;
    private final CityClient cityClient;

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS API for query='{}', cityId={}", query, cityId);

        int duplicateOnlyPointsInRow = 0;

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
        String requestedType = inferRequestedPoiType(query);

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        int gridRadiusKm = safePositive(properties.getGridRadiusKm(), DEFAULT_GRID_RADIUS_KM);
        int cellStepKm = safePositive(properties.getCellStepKm(), DEFAULT_CELL_STEP_KM);
        int radiusMeters = safePositive(properties.getSearchRadiusMeters(), DEFAULT_SEARCH_RADIUS_METERS);
        int pageSize = safePageSize(properties.getPageSize());
        int maxPages = safePositive(properties.getMaxPages(), DEFAULT_MAX_PAGES);

        List<SearchPoint> searchPoints = buildGridPoints(
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
                JsonNode response = fetchPage(
                        webClient,
                        query,
                        point.lng(),
                        point.lat(),
                        radiusMeters,
                        page,
                        pageSize
                );

                if (hasLogicalApiError(response)) {
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

                List<TwoGisRawPoiDto> parsedPage = parseResponse(response, requestedType);
                int addedOnPage = 0;

                for (TwoGisRawPoiDto dto : parsedPage) {
                    String dedupeKey = buildDedupeKey(dto);

                    if (dedupeKey != null && !seenIds.add(dedupeKey)) {
                        continue;
                    }

                    aggregated.add(dto);
                    addedOnPage++;
                }

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

    private JsonNode fetchPage(
            WebClient webClient,
            String query,
            double lng,
            double lat,
            int radiusMeters,
            int page,
            int pageSize
    ) {
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items")
                        .queryParam("q", query)
                        .queryParam("location", lng + "," + lat)
                        .queryParam("radius", radiusMeters)
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("fields",
                                "items.point," +
                                        "items.contact_groups," +
                                        "items.address_name," +
                                        "items.full_address_name," +
                                        "items.schedule," +
                                        "items.address_comment," +
                                        "items.purpose_name," +
                                        "items.site_url," +
                                        "items.rubrics," +
                                        "items.description," +
                                        "items.flags," +
                                        "items.subtitle," +
                                        "items.uri," +
                                        "items.attribute_groups")
                        .queryParam("key", properties.getApiKey())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown 2GIS API error")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "2GIS API request failed: " + clientResponse.statusCode() + ", body=" + body
                                ))))
                .bodyToMono(JsonNode.class)
                .block();

        return response;
    }

    private boolean hasLogicalApiError(JsonNode response) {
        if (response == null) {
            return true;
        }

        int code = response.path("meta").path("code").asInt(200);
        return code != 200;
    }

    private CityExternalDto resolveCity(Long cityId) {
        try {
            return cityClient.getCityById(cityId);
        } catch (Exception ex) {
            log.warn("Failed to resolve city metadata for cityId={}: {}", cityId, ex.getMessage());
            return null;
        }
    }

    private List<SearchPoint> buildGridPoints(
            BigDecimal centerLat,
            BigDecimal centerLng,
            int gridRadiusKm,
            int cellStepKm
    ) {
        List<SearchPoint> points = new ArrayList<>();

        double centerLatVal = centerLat.doubleValue();
        double centerLngVal = centerLng.doubleValue();

        double latStep = kmToLatitudeDegrees(cellStepKm);
        double lngStep = kmToLongitudeDegrees(cellStepKm, centerLatVal);

        int steps = Math.max(1, gridRadiusKm / Math.max(cellStepKm, 1));

        for (int latIndex = -steps; latIndex <= steps; latIndex++) {
            for (int lngIndex = -steps; lngIndex <= steps; lngIndex++) {
                double lat = centerLatVal + latIndex * latStep;
                double lng = centerLngVal + lngIndex * lngStep;

                double distanceKm = approximateDistanceKm(
                        centerLatVal,
                        centerLngVal,
                        lat,
                        lng
                );

                points.add(new SearchPoint(
                        roundCoord(lat),
                        roundCoord(lng),
                        roundCoord(distanceKm)
                ));
            }
        }

        points.sort(Comparator.comparingDouble(SearchPoint::distanceKm));
        return points;
    }

    private double kmToLatitudeDegrees(double km) {
        return km / 111.0;
    }

    private double kmToLongitudeDegrees(double km, double lat) {
        double cos = Math.cos(Math.toRadians(lat));
        if (Math.abs(cos) < 0.0001) {
            cos = 0.0001;
        }
        return km / (111.0 * cos);
    }

    private double approximateDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        double latDiffKm = (lat2 - lat1) * 111.0;
        double lngDiffKm = (lng2 - lng1) * 111.0 * Math.cos(Math.toRadians((lat1 + lat2) / 2.0));
        return Math.sqrt(latDiffKm * latDiffKm + lngDiffKm * lngDiffKm);
    }

    private double roundCoord(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<TwoGisRawPoiDto> parseResponse(JsonNode response, String requestedType) {
        List<TwoGisRawPoiDto> result = new ArrayList<>();

        JsonNode items = response.path("result").path("items");
        if (items.isArray() && !items.isEmpty()) {
            log.info("2GIS raw response result.items size={}", items.size());
            log.info("2GIS first item raw: {}", items.get(0).toPrettyString());
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

            String resolvedType = resolvePoiTypeCode(item);
            if (!matchesRequestedType(requestedType, resolvedType)) {
                continue;
            }

            String externalId = item.path("id").asText(null);
            String sourceUrl = normalizeSourceUrl(item, externalId);
            List<String> rubricNames = extractRubricNames(item);
            String purposeName = normalizeText(item.path("purpose_name").asText(null));
            Map<String, String> features = extractFeatures(item);
            List<TwoGisRawHourDto> hours = extractHours(item);
            String description = buildDescription(item, purposeName, rubricNames, address, features);

            TwoGisRawPoiDto dto = new TwoGisRawPoiDto();
            dto.setExternalId(externalId);
            dto.setName(name);
            dto.setAddress(address);
            dto.setLatitude(lat);
            dto.setLongitude(lon);
            dto.setDescription(description);
            dto.setPhone(extractContactPhone(item));
            dto.setSiteUrl(extractSiteUrl(item));
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

    private List<TwoGisRawHourDto> extractHours(JsonNode item) {
        List<TwoGisRawHourDto> result = new ArrayList<>();
        JsonNode schedule = item.path("schedule");

        if (schedule.isMissingNode() || schedule.isNull() || !schedule.isObject()) {
            return result;
        }

        Map<String, Short> dayMap = Map.of(
                "Sun", (short) 0,
                "Mon", (short) 1,
                "Tue", (short) 2,
                "Wed", (short) 3,
                "Thu", (short) 4,
                "Fri", (short) 5,
                "Sat", (short) 6
        );

        Iterator<Map.Entry<String, JsonNode>> fields = schedule.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String dayCode = entry.getKey();
            JsonNode dayNode = entry.getValue();

            Short dayOfWeek = dayMap.get(dayCode);
            if (dayOfWeek == null || dayNode == null || dayNode.isNull()) {
                continue;
            }

            JsonNode workingHours = dayNode.path("working_hours");
            if (!workingHours.isArray() || workingHours.isEmpty()) {
                continue;
            }

            for (JsonNode interval : workingHours) {
                String from = interval.path("from").asText(null);
                String to = interval.path("to").asText(null);

                if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
                    continue;
                }

                TwoGisRawHourDto dto = new TwoGisRawHourDto();
                dto.setDayOfWeek(dayOfWeek);
                dto.setOpenTime(normalizeTime(from));
                dto.setCloseTime(normalizeTime(to));
                dto.setAroundTheClock(false);
                result.add(dto);
            }
        }

        return result;
    }

    private String normalizeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if ("24:00".equals(normalized)) {
            return "23:59";
        }
        return normalized;
    }

    private String buildDescription(
            JsonNode item,
            String purposeName,
            List<String> rubricNames,
            String address,
            Map<String, String> features
    ) {
        String cleanDescription = cleanHtmlToText(item.path("description").asText(null));
        String subtitle = normalizeText(item.path("subtitle").asText(null));

        if (StringUtils.isNotBlank(cleanDescription) && cleanDescription.length() >= 50) {
            return cleanDescription;
        }

        List<String> sentences = new ArrayList<>();

        String baseType = firstNonBlank(
                normalizePhrase(purposeName),
                normalizePhrase(subtitle),
                buildTypePhrase(rubricNames)
        );

        if (StringUtils.isNotBlank(baseType)) {
            sentences.add(baseType);
        }

        List<String> highlights = extractDescriptionHighlights(features);
        if (!highlights.isEmpty()) {
            String secondSentence = String.join(", ", highlights);
            if (!secondSentence.endsWith(".")) {
                secondSentence += ".";
            }
            sentences.add(capitalizeSentence(secondSentence));
        }

        if (sentences.isEmpty()) {
            if (StringUtils.isNotBlank(address)) {
                return "Объект находится по адресу: " + address + ".";
            }
            return "Информация об объекте ограничена.";
        }

        return String.join(" ", sentences);
    }

    private List<String> extractDescriptionHighlights(Map<String, String> features) {
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : features.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = StringUtils.defaultString(entry.getValue());

            if ((key.contains("wi-fi") || key.contains("wifi")) && "true".equalsIgnoreCase(value)) {
                result.add("Есть Wi-Fi");
            } else if (key.contains("доставка") && "true".equalsIgnoreCase(value)) {
                result.add("Есть доставка");
            } else if (key.contains("навынос") && "true".equalsIgnoreCase(value)) {
                result.add("Можно заказать навынос");
            } else if (key.contains("чек") && value.matches(".*\\d+.*")) {
                result.add("Средний чек — " + value + " ₽");
            } else if (key.contains("мест") && value.matches(".*\\d+.*")) {
                result.add("До " + value + " мест");
            }
        }

        return result.stream().distinct().limit(4).toList();
    }

    private String normalizePhrase(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        normalized = normalized.replaceAll("\\.$", "");
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1) + ".";
    }

    private String buildTypePhrase(List<String> rubricNames) {
        if (rubricNames == null || rubricNames.isEmpty()) {
            return null;
        }

        List<String> filtered = rubricNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .limit(2)
                .toList();

        if (filtered.isEmpty()) {
            return null;
        }

        return String.join(", ", filtered) + ".";
    }

    private void putRawFeature(Map<String, String> target, String groupName, String attributeName, String attributeText) {
        String normalizedGroup = normalizeText(groupName);
        String normalizedName = normalizeText(attributeName);
        String normalizedText = normalizeText(attributeText);

        if (StringUtils.isBlank(normalizedGroup)
                && StringUtils.isBlank(normalizedName)
                && StringUtils.isBlank(normalizedText)) {
            return;
        }

        String key;
        String value;

        if (StringUtils.isNotBlank(normalizedGroup) && StringUtils.isNotBlank(normalizedName)) {
            key = normalizedGroup + "." + normalizedName;
            value = StringUtils.isNotBlank(normalizedText) ? normalizedText : "true";
        } else if (StringUtils.isNotBlank(normalizedName)) {
            key = normalizedName;
            value = StringUtils.isNotBlank(normalizedText) ? normalizedText : "true";
        } else if (StringUtils.isNotBlank(normalizedGroup) && StringUtils.isNotBlank(normalizedText)) {
            key = normalizedGroup + "." + normalizedText;
            value = "true";
        } else if (StringUtils.isNotBlank(normalizedText)) {
            key = normalizedText;
            value = "true";
        } else {
            key = normalizedGroup;
            value = "true";
        }

        target.put(key, value);
    }

    private Map<String, String> extractFeatures(JsonNode item) {
        Map<String, String> result = new LinkedHashMap<>();

        JsonNode attributeGroups = item.path("attribute_groups");
        if (!attributeGroups.isArray()) {
            return result;
        }

        for (JsonNode group : attributeGroups) {
            String groupName = normalizeText(group.path("name").asText(null));
            JsonNode attributes = group.path("attributes");

            if (!attributes.isArray()) {
                continue;
            }

            for (JsonNode attribute : attributes) {
                String attributeName = normalizeText(attribute.path("name").asText(null));
                String attributeText = extractAttributeText(attribute);

                if (StringUtils.isBlank(groupName)
                        && StringUtils.isBlank(attributeName)
                        && StringUtils.isBlank(attributeText)) {
                    continue;
                }

                putRawFeature(result, groupName, attributeName, attributeText);
            }
        }

        return result;
    }

    private String extractAttributeText(JsonNode attribute) {
        String directValue = firstNonBlank(
                normalizeText(attribute.path("text").asText(null)),
                normalizeText(attribute.path("value").asText(null))
        );

        if (StringUtils.isNotBlank(directValue)) {
            return directValue;
        }

        JsonNode values = attribute.path("values");
        if (values.isArray()) {
            List<String> parts = new ArrayList<>();

            for (JsonNode valueNode : values) {
                String value = firstNonBlank(
                        normalizeText(valueNode.path("name").asText(null)),
                        normalizeText(valueNode.path("text").asText(null)),
                        valueNode.isValueNode() ? normalizeText(valueNode.asText(null)) : null
                );

                if (StringUtils.isNotBlank(value)) {
                    parts.add(value);
                }
            }

            if (!parts.isEmpty()) {
                return String.join(", ", parts);
            }
        }

        return null;
    }

    private String capitalizeSentence(String value) {
        String normalized = normalizeText(value);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String cleanHtmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }

        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)<p[^>]*>", "")
                .replaceAll("(?i)<a[^>]*>", "")
                .replaceAll("(?i)</a>", "")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");

        text = normalizeText(text);
        return StringUtils.isBlank(text) ? null : text;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> extractRubricNames(JsonNode item) {
        List<String> result = new ArrayList<>();
        JsonNode rubrics = item.path("rubrics");
        if (!rubrics.isArray()) {
            return result;
        }

        for (JsonNode rubric : rubrics) {
            String name = rubric.path("name").asText(null);
            if (StringUtils.isNotBlank(name)) {
                result.add(name.trim());
            }
        }

        return result;
    }

    private String extractContactPhone(JsonNode item) {
        JsonNode contacts = item.path("contact_groups");
        if (contacts.isArray()) {
            for (JsonNode group : contacts) {
                JsonNode contactsArray = group.path("contacts");
                if (contactsArray.isArray()) {
                    for (JsonNode contact : contactsArray) {
                        String type = contact.path("type").asText("");
                        if ("phone".equalsIgnoreCase(type)) {
                            JsonNode value = contact.path("value");
                            if (!value.isMissingNode() && !value.isNull()) {
                                String phone = value.asText(null);
                                if (StringUtils.isNotBlank(phone)) {
                                    return phone;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String extractSiteUrl(JsonNode item) {
        JsonNode siteUrl = item.path("site_url");
        if (!siteUrl.isMissingNode() && !siteUrl.isNull()) {
            String value = siteUrl.asText(null);
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
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

    private String resolvePoiTypeCode(JsonNode item) {
        List<String> rubrics = extractRubricNames(item);
        String text = String.join(" ",
                StringUtils.defaultString(item.path("name").asText("")),
                StringUtils.defaultString(item.path("subtitle").asText("")),
                StringUtils.defaultString(item.path("purpose_name").asText("")),
                String.join(" ", rubrics)
        ).toLowerCase();

        if (containsAny(text, "кафе", "кофейня", "coffee")) return "cafe";
        if (containsAny(text, "ресторан", "бар", "паб", "столовая", "пиццерия", "бургер")) return "restaurant";
        if (containsAny(text, "отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы", "апартаменты")) return "hotel";
        if (containsAny(text, "парк", "сквер", "сад")) return "park";
        if (containsAny(text, "музей", "собор", "храм", "театр", "памятник", "достопримечательность", "галерея")) return "landmark";
        if (containsAny(text, "магазин", "shop")) return "shop";
        if (containsAny(text, "аптека")) return "pharmacy";
        if (containsAny(text, "больница", "клиника")) return "hospital";
        if (containsAny(text, "школа", "университет")) return "school";
        if (containsAny(text, "банкомат", "atm")) return "atm";

        return "landmark";
    }

    private String inferRequestedPoiType(String query) {
        String normalized = StringUtils.defaultString(query).toLowerCase();

        if (containsAny(normalized, "кафе", "кофейня", "кофейни")) return "cafe";
        if (containsAny(normalized, "ресторан", "рестораны", "бар", "паб", "пиццерия", "фастфуд")) return "restaurant";
        if (containsAny(normalized, "отель", "отели", "гостиница", "гостиницы", "хостел", "хостелы")) return "hotel";
        if (containsAny(normalized, "парк", "парки", "сквер", "сад")) return "park";
        if (containsAny(normalized, "музей", "музеи", "театр", "театры", "собор", "храм", "памятник", "достопримечательность")) return "landmark";

        return null;
    }

    private boolean matchesRequestedType(String requestedType, String resolvedType) {
        return requestedType == null || StringUtils.equalsIgnoreCase(requestedType, resolvedType);
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) {
            return false;
        }
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
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

    private record SearchPoint(double lat, double lng, double distanceKm) {
    }
}