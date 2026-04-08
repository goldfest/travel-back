package com.travelapp.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.config.TwoGisProperties;
import com.travelapp.poi.model.entity.CityExternalDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawHourDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawMediaDto;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisClient {

    private final TwoGisProperties properties;
    private final CityClient cityClient;

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS API for query='{}', cityId={}", query, cityId);

        String tempQuery = query;

        if (cityId != null) {
            try {
                CityExternalDto city = cityClient.getCityById(cityId);
                if (city != null && city.getName() != null && !city.getName().isBlank()) {
                    tempQuery = query + " " + city.getName().trim();
                }
            } catch (Exception ex) {
                log.warn("Failed to resolve city name for cityId={}, using original query='{}'. Error={}",
                        cityId, query, ex.getMessage());
            }
        }

        final String effectiveQuery = tempQuery;

        log.info("Effective 2GIS query='{}'", effectiveQuery);

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();


        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items")
                        .queryParam("q", effectiveQuery)
                        .queryParam("fields",
                                "items.point," +
                                        "items.contact_groups," +
                                        "items.full_address_name," +
                                        "items.schedule," +
                                        "items.address_comment," +
                                        "items.purpose_name," +
                                        "items.site_url," +
                                        "items.rubrics," +
                                        "items.description," +
                                        "items.flags," +
                                        "items.photos")
                        .queryParam("key", properties.getApiKey())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown 2GIS API error")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "2GIS API request failed: " + clientResponse.statusCode() + ", body=" + body
                                )))
                )
                .bodyToMono(JsonNode.class)
                .block();
        log.info("2GIS raw response result.items size={}",
                response != null && response.path("result").path("items").isArray()
                        ? response.path("result").path("items").size()
                        : -1);

        return parseResponse(response);
    }

    private List<TwoGisRawPoiDto> parseResponse(JsonNode response) {
        List<TwoGisRawPoiDto> result = new ArrayList<>();

        JsonNode items = response.path("result").path("items");
        if (items.isArray() && !items.isEmpty()) {
            log.info("2GIS raw response result.items size={}", items.size());
            log.info("2GIS first item raw: {}", items.get(0).toPrettyString());
        }

        for (JsonNode item : items) {
            String name = item.path("name").asText(null);
            String address = item.path("address_name").asText(null);

            JsonNode point = item.path("point");
            Double lat = point.has("lat") && !point.path("lat").isNull()
                    ? point.path("lat").asDouble()
                    : null;
            Double lon = point.has("lon") && !point.path("lon").isNull()
                    ? point.path("lon").asDouble()
                    : null;

            if (name == null || name.isBlank()) {
                log.warn("Skipping 2GIS item without name: {}", item);
                continue;
            }

            if (lat == null || lon == null) {
                log.warn("Skipping 2GIS item without coordinates: name={}, item={}", name, item);
                continue;
            }

            TwoGisRawPoiDto dto = new TwoGisRawPoiDto();
            dto.setExternalId(item.path("id").asText(null));
            dto.setName(name);
            dto.setAddress(address != null ? address : "Адрес не указан");
            dto.setLatitude(lat);
            dto.setLongitude(lon);

            dto.setDescription(buildDescription(item));
            dto.setPhone(extractContactPhone(item));
            dto.setSiteUrl(extractSiteUrl(item));
            dto.setPriceLevel(0);
            dto.setPoiTypeCode(resolvePoiTypeCode(item));

            String externalId = item.path("id").asText(null);
            dto.setExternalId(externalId);

            String uri = item.path("uri").asText(null);

            if (uri != null && !uri.isBlank()) {
                dto.setSourceUrl(uri);
            } else if (externalId != null && !externalId.isBlank()) {
                dto.setSourceUrl("2gis:item:" + externalId);
            } else {
                dto.setSourceUrl(null);
            }
            dto.setFeatures(Map.of());
            dto.setHours(extractHours(item));
            dto.setMedia(extractMedia(item));

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

    private List<TwoGisRawMediaDto> extractMedia(JsonNode item) {
        List<TwoGisRawMediaDto> result = new ArrayList<>();

        JsonNode photos = item.path("photos");
        if (photos.isArray()) {
            for (JsonNode photo : photos) {
                String previewUrl = photo.path("preview_url").asText(null);
                if (previewUrl != null && !previewUrl.isBlank()) {
                    TwoGisRawMediaDto dto = new TwoGisRawMediaDto();
                    dto.setUrl(previewUrl);
                    dto.setMediaType("IMAGE");
                    result.add(dto);
                }
            }
        }

        if (result.isEmpty() && hasPhotosFlag(item)) {
            String staticMapUrl = buildStaticMapUrlFromItem(item);
            if (staticMapUrl != null) {
                TwoGisRawMediaDto dto = new TwoGisRawMediaDto();
                dto.setUrl(staticMapUrl);
                dto.setMediaType("IMAGE");
                result.add(dto);
            }
        }

        return result;
    }

    private boolean hasPhotosFlag(JsonNode item) {
        return item.path("flags").path("photos").asBoolean(false);
    }

    private String buildDescription(JsonNode item) {
        List<String> parts = new ArrayList<>();

        String purpose = normalizeText(item.path("purpose_name").asText(null));
        String subtypeName = normalizeText(item.path("subtype_name").asText(null));
        String address = firstNonBlank(
                normalizeText(item.path("full_address_name").asText(null)),
                normalizeText(item.path("address_name").asText(null))
        );

        String rawDescription = item.path("description").asText(null);
        String description = cleanHtmlToText(rawDescription);

        List<String> rubrics = extractRubricNames(item);

        String typePart = firstNonBlank(purpose, subtypeName);

        if (typePart != null) {
            parts.add(typePart);
        }

        if (!rubrics.isEmpty()) {
            parts.add("Категории: " + String.join(", ", rubrics));
        }

        if (address != null) {
            parts.add("Адрес: " + address);
        }

        if (description != null) {
            parts.add(description);
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

        if (text == null || text.isBlank()) {
            return null;
        }

        return text;
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
            if (value != null && !value.isBlank()) {
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
            if (name != null && !name.isBlank()) {
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
                                if (phone != null && !phone.isBlank()) {
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
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolvePoiTypeCode(JsonNode item) {
        String name = item.path("name").asText("").toLowerCase();
        String subtitle = item.path("subtitle").asText("").toLowerCase();

        String text = name + " " + subtitle;

        if (text.contains("ресторан") || text.contains("кафе")) {
            return "restaurant";
        }
        if (text.contains("отель") || text.contains("гостиница") || text.contains("хостел")) {
            return "hotel";
        }
        if (text.contains("музей") || text.contains("собор") || text.contains("театр")
                || text.contains("памятник") || text.contains("достопримечатель")) {
            return "landmark";
        }

        return "landmark";
    }
}