package com.travelapp.poi.security;

import com.travelapp.poi.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return p;
    }

    public static Long requireUserId() {
        return requirePrincipal().getId();
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String need = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (need.equals(a.getAuthority())) return true;
        }
        return false;
    }

    public static void requireAdmin() {
        if (!hasRole("ADMIN")) {
            throw new com.travelapp.poi.exception.ValidationException("Admin only");
        }
    }
}