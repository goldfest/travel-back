package com.travelapp.poi.service.security.dto;

import lombok.Data;

@Data
public class AuthUser {
    private Long id;
    private String role;
    private String status;
    private Boolean isBlocked;

    public boolean isAdmin() {
        if (role == null) return false;
        String r = role.trim().toUpperCase();
        return r.equals("ADMIN") || r.equals("ROLE_ADMIN");
    }
}