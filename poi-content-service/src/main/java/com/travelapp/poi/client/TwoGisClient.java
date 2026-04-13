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

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisClient {

    private final TwoGisProperties properties;
    private final CityClient cityClient;

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS API for query='{}', cityId={}", query, cityId);

        CityExternalDto city = resolveCity(cityId);
        String cityName = city != null ? StringUtils.trimToNull(city.getName()) : null;

        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("2GIS query must not be blank");
        }
        if (StringUtils.isBlank(cityName)) {
            throw new IllegalArgumentException("City name is required to search in 2GIS");
        }

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        String twoGisCityId = resolveTwoGisCityId(webClient, cityName);
        String requestedType = inferRequestedPoiType(query);

        log.info("2GIS search context: query='{}', localCityId={}, cityName='{}', twoGisCityId='{}', requestedType={}, pageSize={}, maxPages={}",
                query, cityId, cityName, twoGisCityId, requestedType, properties.getPageSize(), properties.getMaxPages());

        List<TwoGisRawPoiDto> aggregated = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        int pageSize = properties.getPageSize() != null && properties.getPageSize() > 0
                ? properties.getPageSize()
                : 50;

        int maxPages = properties.getMaxPages() != null && properties.getMaxPages() > 0
                ? properties.getMaxPages()
                : 20;

        for (int page = 1; page <= maxPages; page++) {
            JsonNode response = fetchPage(webClient, query, twoGisCityId, page, pageSize);
            JsonNode items = response.path("result").path("items");

            int pageItems = items.isArray() ? items.size() : 0;
            int total = response.path("result").path("total").asInt(-1);

            log.info("2GIS page {} fetched: pageItems={}, reportedTotal={}", page, pageItems, total);

            if (pageItems == 0) {
                break;
            }

            List<TwoGisRawPoiDto> parsedPage = parseResponse(response, requestedType);
            int addedOnPage = 0;

            for (TwoGisRawPoiDto dto : parsedPage) {
                String dedupeKey = firstNonBlank(
                        dto.getExternalId(),
                        dto.getSourceUrl(),
                        dto.getName() + "|" + dto.getAddress()
                );

                if (dedupeKey != null && !seenIds.add(dedupeKey)) {
                    continue;
                }

                aggregated.add(dto);
                addedOnPage++;
            }

            log.info("2GIS page {} parsed: acceptedOnPage={}, aggregated={}", page, addedOnPage, aggregated.size());

            if (pageItems < pageSize) {
                break;
            }

            if (total > 0 && aggregated.size() >= total) {
                break;
            }
        }

        log.info("2GIS search completed: aggregatedResults={}, query='{}', cityId={}",
                aggregated.size(), query, cityId);

        return aggregated;
    }

    private JsonNode fetchPage(
            WebClient webClient,
            String query,
            String twoGisCityId,
            int page,
            int pageSize
    ) {

        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items")
                        .queryParam("q", query)
                        .queryParam("city_id", twoGisCityId)
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
                                        "items.photos," +
                                        "items.subtitle," +
                                        "items.uri")
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

        log.info("2GIS raw response page {}: {}", page, response != null ? response.toPrettyString() : null);
        return response;
    }

    private String resolveTwoGisCityId(WebClient webClient, String cityName) {
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items/geocode")
                        .queryParam("q", cityName)
                        .queryParam("type", "adm_div.city")
                        .queryParam("key", properties.getApiKey())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown 2GIS geocode error")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "2GIS geocode failed: " + clientResponse.statusCode() + ", body=" + body
                                ))))
                .bodyToMono(JsonNode.class)
                .block();

        log.info("2GIS geocode city response for '{}': {}", cityName, response != null ? response.toPrettyString() : null);

        JsonNode items = response.path("result").path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new IllegalStateException("2GIS city geocode returned no results for city: " + cityName);
        }

        for (JsonNode item : items) {
            String subtype = item.path("subtype").asText("");
            String id = item.path("id").asText(null);

            if ("city".equalsIgnoreCase(subtype) && StringUtils.isNotBlank(id)) {
                return id;
            }
        }

        String fallbackId = items.get(0).path("id").asText(null);
        if (StringUtils.isBlank(fallbackId)) {
            throw new IllegalStateException("2GIS city geocode returned items without id for city: " + cityName);
        }

        return fallbackId;
    }

    private CityExternalDto resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }

        try {
            return cityClient.getCityById(cityId);
        } catch (Exception ex) {
            log.warn("Failed to resolve city metadata for cityId={}: {}", cityId, ex.getMessage());
            return null;
        }
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

            if (StringUtils.isBlank(name)) {
                log.warn("Skipping 2GIS item without name: {}", item);
                continue;
            }

            if (lat == null || lon == null) {
                log.warn("Skipping 2GIS item without coordinates: name={}, item={}", name, item);
                continue;
            }

            String resolvedType = resolvePoiTypeCode(item);
            if (!matchesRequestedType(requestedType, resolvedType)) {
                log.debug("Skipping 2GIS item due to requestedType mismatch. name={}, requestedType={}, resolvedType={}",
                        name, requestedType, resolvedType);
                continue;
            }

            String externalId = item.path("id").asText(null);
            String sourceUrl = normalizeSourceUrl(item, externalId);
            List<String> rubricNames = extractRubricNames(item);
            String purposeName = normalizeText(item.path("purpose_name").asText(null));
            boolean hasPhotos = hasPhotosFlag(item);
            String staticMapUrl = buildStaticMapUrlFromItem(item);

            TwoGisRawPoiDto dto = new TwoGisRawPoiDto();
            dto.setExternalId(externalId);
            dto.setName(name);
            dto.setAddress(address);
            dto.setLatitude(lat);
            dto.setLongitude(lon);
            dto.setDescription(buildDescription(item, purposeName, rubricNames, address));
            dto.setPhone(extractContactPhone(item));
            dto.setSiteUrl(extractSiteUrl(item));
            dto.setPriceLevel(0);
            dto.setPoiTypeCode(resolvedType);
            dto.setSourceUrl(sourceUrl);
            dto.setFeatures(Map.of());
            dto.setHours(extractHours(item));
            dto.setMedia(extractMedia(item, staticMapUrl, hasPhotos));
            dto.setPurposeName(purposeName);
            dto.setRubricNames(rubricNames);
            dto.setHasPhotos(hasPhotos);
            dto.setStaticMapUrl(staticMapUrl);

            result.add(dto);
        }

        return result;
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

    private List<TwoGisRawMediaDto> extractMedia(JsonNode item, String staticMapUrl, boolean hasPhotos) {
        List<TwoGisRawMediaDto> result = new ArrayList<>();

        JsonNode photos = item.path("photos");
        if (photos.isArray()) {
            for (JsonNode photo : photos) {
                String photoUrl = firstNonBlank(
                        normalizeText(photo.path("preview_url").asText(null)),
                        normalizeText(photo.path("url").asText(null)),
                        normalizeText(photo.path("source").asText(null))
                );
                if (photoUrl != null) {
                    TwoGisRawMediaDto dto = new TwoGisRawMediaDto();
                    dto.setUrl(photoUrl);
                    dto.setMediaType("IMAGE");
                    result.add(dto);
                }
            }
        }

        if (result.isEmpty() && hasPhotos && staticMapUrl != null) {
            TwoGisRawMediaDto dto = new TwoGisRawMediaDto();
            dto.setUrl(staticMapUrl);
            dto.setMediaType("IMAGE");
            result.add(dto);
        }

        return result;
    }

    private boolean hasPhotosFlag(JsonNode item) {
        return item.path("flags").path("photos").asBoolean(false);
    }

    private String buildDescription(JsonNode item, String purposeName, List<String> rubricNames, String address) {
        List<String> parts = new ArrayList<>();

        String description = cleanHtmlToText(item.path("description").asText(null));
        String subtitle = normalizeText(item.path("subtitle").asText(null));
        String siteUrl = extractSiteUrl(item);

        if (purposeName != null) {
            parts.add(purposeName);
        } else if (subtitle != null) {
            parts.add(subtitle);
        }

        if (!rubricNames.isEmpty()) {
            parts.add("Категории: " + String.join(", ", rubricNames));
        }

        if (description != null) {
            parts.add(description);
        }

        if (address != null) {
            parts.add("Адрес: " + address);
        }

        if (siteUrl != null) {
            parts.add("Сайт: " + siteUrl);
        }

        if (parts.isEmpty()) {
            return "Описание объекта временно отсутствует.";
        }

        return String.join(". ", parts) + ".";
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

    private String buildStaticMapUrlFromItem(JsonNode item) {
        JsonNode point = item.path("point");

        Double lat = point.has("lat") && !point.path("lat").isNull()
                ? point.path("lat").asDouble()
                : null;
        Double lon = point.has("lon") && !point.path("lon").isNull()
                ? point.path("lon").asDouble()
                : null;

        if (lat == null || lon == null) {
            return null;
        }

        return "https://static.maps.2gis.com/2.0"
                + "?s=800x450"
                + "&z=16"
                + "&pt=" + lon + "," + lat + "~k:p"
                + "&key=" + properties.getApiKey();
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
}