package com.travelapp.auth.service;

import com.travelapp.auth.model.entity.RefreshToken;
import com.travelapp.auth.model.entity.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    RefreshToken findByToken(String token);

    RefreshToken verifyExpiration(RefreshToken token);

    void revokeRefreshToken(RefreshToken token);

    void revokeByToken(String token);

    void revokeAllUserTokens(User user);

    void deleteExpiredTokens();
}