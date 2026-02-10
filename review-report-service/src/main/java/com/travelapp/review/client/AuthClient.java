package com.travelapp.review.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auth-service", url = "${service.auth.url}")
public interface AuthClient {

    @GetMapping("/internal/users/{userId}/info")
    Map<String, Object> getUserInfo(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/{userId}/exists")
    Boolean checkUserExists(@PathVariable("userId") Long userId);

    @GetMapping("/internal/users/validate-token")
    Map<String, Object> validateToken(@RequestParam("token") String token);
}