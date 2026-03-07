package com.travelapp.city.security;

import com.travelapp.city.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return p;
    }

    public static Long requireUserId() {
        AuthPrincipal p = requirePrincipal();
        if (p.getId() == null) throw new UnauthorizedException("Unauthorized");
        return p.getId();
    }

    public static Long getHomeCityIdOrNull() {
        return requirePrincipal().getHomeCityId();
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
}