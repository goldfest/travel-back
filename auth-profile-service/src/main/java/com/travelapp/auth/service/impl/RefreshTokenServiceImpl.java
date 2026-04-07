package com.travelapp.auth.service.impl;

import com.travelapp.auth.exception.TokenRefreshException;
import com.travelapp.auth.model.entity.RefreshToken;
import com.travelapp.auth.model.entity.User;
import com.travelapp.auth.repository.RefreshTokenRepository;
import com.travelapp.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    @Value("${app.security.jwt.refresh-expiration}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        log.debug("Creating refresh token for user: {}", user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(LocalDateTime.now().plus(refreshTokenDurationMs, ChronoUnit.MILLIS))
                .revoked(false)
                .createdAt(LocalDateTime.now())
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken findByToken(String token) {
        log.debug("Finding refresh token by token");
        return refreshTokenRepository.findByToken(token)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(LocalDateTime.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("Refresh token was expired. Please make a new login request");
        }
        return token;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(RefreshToken token) {
        log.debug("Revoking refresh token: {}", token.getId());
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void revokeByToken(String token) {
        log.debug("Revoking refresh token by token string");
        refreshTokenRepository.findByToken(token).ifPresent(this::revokeRefreshToken);
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        log.info("Revoking all refresh tokens for user: {}", user.getId());
        refreshTokenRepository.revokeAllUserTokens(user);
        refreshTokenRepository.deleteAllRevokedByUser(user);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 2 * * ?") // Ежедневно в 2:00 ночи
    public void deleteExpiredTokens() {
        log.info("Deleting expired refresh tokens");
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.deleteAllExpiredSince(now);
        log.info("Expired refresh tokens cleanup completed");
    }
}