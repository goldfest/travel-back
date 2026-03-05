package com.travelapp.city.service.security;

import com.travelapp.city.service.security.dto.AuthUser;

public interface AuthClient {
    AuthUser getCurrentUser(String bearerToken);
}