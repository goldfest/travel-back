package com.travelapp.notification.client;

import com.travelapp.notification.model.dto.InternalUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "auth-service",
        url = "${services.auth.url}"
)
public interface AuthClient {

    @GetMapping("/internal/users/me")
    InternalUserResponse getCurrentUser(@RequestHeader("Authorization") String authorizationHeader);

    @GetMapping("/internal/users/{id}/info")
    InternalUserResponse getUserInfo(@PathVariable("id") Long userId);

    @GetMapping("/internal/users/{id}/exists")
    Boolean exists(@PathVariable("id") Long userId);
}