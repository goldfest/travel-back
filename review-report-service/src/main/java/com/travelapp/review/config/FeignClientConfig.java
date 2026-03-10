package com.travelapp.review.config;

import com.travelapp.review.security.BearerTokenProvider;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
@RequiredArgsConstructor
public class FeignClientConfig {

    private final BearerTokenProvider bearerTokenProvider;

    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return template -> {
            String authHeader = bearerTokenProvider.getCurrentAuthorizationHeader();
            if (authHeader != null && !authHeader.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authHeader);
            }
        };
    }
}