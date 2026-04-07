package com.travelapp.auth.service;

import com.travelapp.auth.model.dto.request.LoginRequest;
import com.travelapp.auth.model.dto.request.RefreshTokenRequest;
import com.travelapp.auth.model.dto.request.RegisterRequest;
import com.travelapp.auth.model.dto.response.AuthResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(String refreshToken);

    void logoutAll(Long userId);
}