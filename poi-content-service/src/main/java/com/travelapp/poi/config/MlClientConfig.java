package com.travelapp.poi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class MlClientConfig {

    @Bean
    public WebClient mlWebClient(
            @Value("${services.ml.base-url}") String mlBaseUrl
    ) {
        return WebClient.builder()
                .baseUrl(mlBaseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}