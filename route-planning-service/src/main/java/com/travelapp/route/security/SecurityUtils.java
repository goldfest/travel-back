package com.travelapp.route.security;

import com.travelapp.route.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthPrincipal requirePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new UnauthorizedException("Unauthorized");
        }
        return principal;
    }

    public static Long requireUserId() {
        return requirePrincipal().getId();
    }
}