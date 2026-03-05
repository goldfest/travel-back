package com.travelapp.poi.service.security;

import com.travelapp.poi.service.security.dto.AuthUser;

public interface AuthClient {

    /** Вернёт текущего пользователя по access-token (GET /users/me). */
    AuthUser getCurrentUser(String bearerToken);

    /** Быстрая проверка роли ADMIN по access-token. */
    default boolean isAdmin(String bearerToken) {
        AuthUser u = getCurrentUser(bearerToken);
        return u != null && u.isAdmin();
    }

    /** Достать userId из access-token через /users/me. */
    default Long requireUserId(String bearerToken) {
        AuthUser u = getCurrentUser(bearerToken);
        if (u == null || u.getId() == null) {
            throw new com.travelapp.poi.exception.UnauthorizedException("Unauthorized");
        }
        return u.getId();
    }
}