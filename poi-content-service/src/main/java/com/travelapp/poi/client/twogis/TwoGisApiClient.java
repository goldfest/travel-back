package com.travelapp.poi.client.twogis;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelapp.poi.config.TwoGisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwoGisApiClient {

    private static final String FIELDS =
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
                    "items.attribute_groups";

    private final TwoGisProperties properties;

    public JsonNode fetchPage(String query,
                              double lng,
                              double lat,
                              int radiusMeters,
                              int page,
                              int pageSize) {
        WebClient webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/3.0/items")
                        .queryParam("q", query)
                        .queryParam("location", lng + "," + lat)
                        .queryParam("radius", radiusMeters)
                        .queryParam("page", page)
                        .queryParam("page_size", pageSize)
                        .queryParam("fields", FIELDS)
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
    }

    public boolean hasLogicalApiError(JsonNode response) {
        if (response == null) {
            return true;
        }

        int code = response.path("meta").path("code").asInt(200);
        return code != 200;
    }
}
