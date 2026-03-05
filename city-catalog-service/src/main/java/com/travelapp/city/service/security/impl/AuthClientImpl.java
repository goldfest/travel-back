package com.travelapp.city.service.security.impl;

import com.travelapp.city.config.AuthServiceProperties;
import com.travelapp.city.service.security.AuthClient;
import com.travelapp.city.service.security.dto.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthClientImpl implements AuthClient {

    private final WebClient authWebClient;
    private final AuthServiceProperties props;

    @Override
    public AuthUser getCurrentUser(String bearerToken) {
        String token = normalizeBearer(bearerToken);

        return authWebClient.get()
                .uri(props.getMePath())
                .header(HttpHeaders.AUTHORIZATION, token)
                .retrieve()
                .onStatus(s -> s.value() == 401 || s.value() == 403,
                        r -> Mono.<Throwable>error(new RuntimeException("Unauthorized")))
                .onStatus(HttpStatusCode::isError,
                        r -> r.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.<Throwable>error(
                                        new RuntimeException("Auth error " + r.statusCode() + " body=" + body)
                                )))
                .bodyToMono(AuthUser.class)
                .block();
    }

    private String normalizeBearer(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new RuntimeException("Missing Authorization header");
        }
        String t = bearerToken.trim();
        return t.startsWith("Bearer ") ? t : "Bearer " + t;
    }
}