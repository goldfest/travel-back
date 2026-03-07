package com.travelapp.review.service;

import com.travelapp.review.client.AuthClient;
import com.travelapp.review.exception.ForbiddenException;
import com.travelapp.review.exception.UnauthorizedException;
import com.travelapp.review.model.dto.InternalUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthUserService {

    private final AuthClient authClient;

    public InternalUserResponse getCurrentUser(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new UnauthorizedException("Missing Authorization header");
        }

        InternalUserResponse user = authClient.getCurrentUser(authorizationHeader);

        if (user == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return user;
    }

    public InternalUserResponse getActiveCurrentUser(String authorizationHeader) {
        InternalUserResponse user = getCurrentUser(authorizationHeader);
        ensureActive(user);
        return user;
    }

    public InternalUserResponse getUserInfo(Long userId) {
        return authClient.getUserInfo(userId);
    }

    public boolean exists(Long userId) {
        return Boolean.TRUE.equals(authClient.exists(userId));
    }

    public void validateExists(Long userId) {
        if (!exists(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
    }

    public void ensureActive(InternalUserResponse user) {
        if (user == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new ForbiddenException("User is blocked");
        }

        if (user.getStatus() == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ForbiddenException("User is not active");
        }
    }

    public void ensureAdmin(InternalUserResponse user) {
        ensureActive(user);

        if (user.getRole() == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ForbiddenException("Admin role required");
        }
    }
}