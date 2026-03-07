package com.travelapp.review.security;

import lombok.Value;

@Value
public class AuthPrincipal {
    Long id;
    String role;
    String status;
    Boolean isBlocked;

    public boolean isAdmin() {
        if (role == null) return false;
        String r = role.trim().toUpperCase();
        return r.equals("ADMIN") || r.equals("ROLE_ADMIN");
    }
}