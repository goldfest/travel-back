package com.travelapp.review.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenProvider {

    public String getCurrentAuthorizationHeader() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object credentials = authentication.getCredentials();
        if (credentials == null) {
            return null;
        }

        String authHeader = credentials.toString();
        if (authHeader.isBlank()) {
            return null;
        }

        return authHeader;
    }
}