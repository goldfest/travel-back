package com.travelapp.poi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.config.TwoGisProperties;
import com.travelapp.poi.model.imports.twogis.TwoGisRawPoiDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisClient {

    private final TwoGisProperties properties;

    public List<TwoGisRawPoiDto> search(String query, Long cityId) {
        log.info("Searching 2GIS API for query='{}', cityId={}", query, cityId);

        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items")
                        .queryParam("q", query)
                        .queryParam("fields", "items.point,items.contact_groups,items.full_address_name,items.schedule,items.address_comment,items.purpose_name,items.site_url,items.rubrics")
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

            result.add(dto);
        }

        return result;
    }

    private String buildDescription(JsonNode item) {
        StringBuilder sb = new StringBuilder();

        String subtitle = item.path("subtitle").asText("");
        String address = item.path("address_name").asText("");

        if (!subtitle.isBlank()) {
            sb.append(subtitle).append(". ");
        }

        if (!address.isBlank()) {
            sb.append("Расположен по адресу: ").append(address).append(".");
        }

        String description = sb.toString().trim();

        if (description.isBlank()) {
            return "Описание объекта временно отсутствует.";
        }

        return description;
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