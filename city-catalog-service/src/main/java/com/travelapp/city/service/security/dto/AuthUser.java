package com.travelapp.city.service.security.dto;

import lombok.Data;

@Data
public class AuthUser {
    private Long id;
    private String role;
    private String status;
    private Boolean isBlocked;

    // ВАЖНО: auth-service должен возвращать homeCityId в /users/me
    private Long homeCityId;
}