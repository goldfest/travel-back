package com.travelapp.poi.client;

import com.travelapp.poi.model.ml.MlEnrichResponse;
import com.travelapp.poi.model.ml.request.MlEnrichRawRequest;
import com.travelapp.poi.model.ml.request.MlImportFromSourceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MlPoiWorkerClient {

    private final WebClient mlWebClient;

    public MlEnrichResponse enrichRaw(MlEnrichRawRequest request) {
        log.info("Calling ML worker enrich-raw for cityId={}", request.getCityId());

        return mlWebClient.post()
                .uri("/api/v1/poi/enrich-raw")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown ML service error")
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("ML enrich-raw failed: " + response.statusCode() + ", body=" + body)
                                )))
                .bodyToMono(MlEnrichResponse.class)
                .block();
    }

    public String getHealth() {
        return mlWebClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public String getModelInfo() {
        return mlWebClient.get()
                .uri("/api/v1/model/info")
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public MlEnrichResponse importFromSource(MlImportFromSourceRequest request) {
        log.info("Calling ML worker import-from-source for cityId={}, sourceCode={}",
                request.getCityId(), request.getSourceCode());

        return mlWebClient.post()
                .uri("/api/v1/poi/import-from-source")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("Unknown ML service error")
                                .flatMap(body -> Mono.error(
                                        new RuntimeException("ML import-from-source failed: " + response.statusCode() + ", body=" + body)
                                )))
                .bodyToMono(MlEnrichResponse.class)
                .block();
    }
}