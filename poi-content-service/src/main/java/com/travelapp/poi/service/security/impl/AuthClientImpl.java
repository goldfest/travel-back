package com.travelapp.poi.service.security.impl;

import com.travelapp.poi.config.AuthServiceProperties;
import com.travelapp.poi.exception.UnauthorizedException;
import com.travelapp.poi.service.security.AuthClient;
import com.travelapp.poi.service.security.dto.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

        try {
            return authWebClient.get()
                    .uri(props.getMePath())
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 401 || status.value() == 403,
                            resp -> Mono.<Throwable>error(new UnauthorizedException("Unauthorized"))
                    )
                    .onStatus(
                            HttpStatusCode::isError,
                            resp -> resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(body ->
                                            Mono.<Throwable>error(new RuntimeException(
                                                    "Auth service error " + resp.statusCode() + " body=" + body
                                            ))
                                    )
                    )
                    .bodyToMono(AuthUser.class)
                    .block();

        } catch (UnauthorizedException e) {
            throw e;
        } catch (WebClientResponseException e) {
            // на случай если retrieve() всё же выбросил исключение
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                throw new UnauthorizedException("Unauthorized");
            }
            log.warn("AuthClient WebClientResponseException: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new UnauthorizedException("Auth service error");
        } catch (Exception e) {
            log.warn("AuthClient getCurrentUser failed: {}", e.toString());
            throw new UnauthorizedException("Auth service unavailable or token invalid");
        }
    }

    private String normalizeBearer(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new UnauthorizedException("Missing Authorization header");
        }
        String t = bearerToken.trim();
        return t.startsWith("Bearer ") ? t : "Bearer " + t;
    }
}