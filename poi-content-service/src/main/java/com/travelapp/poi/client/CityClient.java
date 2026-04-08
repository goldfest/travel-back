package com.travelapp.poi.client;

import com.travelapp.poi.model.entity.CityExternalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class CityClient {

    private final WebClient cityWebClient;

    public CityExternalDto getCityById(Long cityId) {
        log.info("Fetching city by id={}", cityId);

        return cityWebClient.get()
                .uri("/v1/{id}", cityId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown city service error")
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("City service request failed: "
                                                + response.statusCode() + ", body=" + body)
                                )))
                .bodyToMono(CityExternalDto.class)
                .block();
    }
}
