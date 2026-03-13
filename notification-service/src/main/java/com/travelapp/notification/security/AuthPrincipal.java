package com.travelapp.notification.security;

import lombok.Value;

@Value
public class AuthPrincipal {
    Long id;
    String role;
    String status;
    Boolean isBlocked;

    public boolean isAdmin() {
        if (role == null) {
            return false;
        }
        String normalized = role.trim().toUpperCase();
        return normalized.equals("ADMIN") || normalized.equals("ROLE_ADMIN");
    }
}