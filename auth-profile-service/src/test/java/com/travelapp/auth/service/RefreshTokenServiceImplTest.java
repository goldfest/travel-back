package com.travelapp.auth.service;

import com.travelapp.auth.exception.TokenRefreshException;
import com.travelapp.auth.model.entity.RefreshToken;
import com.travelapp.auth.model.entity.User;
import com.travelapp.auth.repository.RefreshTokenRepository;
import com.travelapp.auth.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    @Test
    void createRefreshToken_shouldCreateTokenWithExpiryDateAndSaveIt() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDurationMs", 86_400_000L);
        User user = user(1L);

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken result = refreshTokenService.createRefreshToken(user);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getRevoked()).isFalse();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getExpiryDate()).isAfter(LocalDateTime.now().plusHours(23));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    void findByToken_shouldReturnToken_whenTokenExists() {
        RefreshToken token = refreshToken("refresh-token", user(1L), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        RefreshToken result = refreshTokenService.findByToken("refresh-token");

        assertThat(result).isEqualTo(token);
    }

    @Test
    void findByToken_shouldReturnNull_whenTokenDoesNotExist() {
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        RefreshToken result = refreshTokenService.findByToken("unknown-token");

        assertThat(result).isNull();
    }

    @Test
    void verifyExpiration_shouldReturnToken_whenTokenIsNotExpired() {
        RefreshToken token = refreshToken("refresh-token", user(1L), LocalDateTime.now().plusMinutes(10), false);

        RefreshToken result = refreshTokenService.verifyExpiration(token);

        assertThat(result).isEqualTo(token);
        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void verifyExpiration_shouldDeleteTokenAndThrowException_whenTokenIsExpired() {
        RefreshToken token = refreshToken("expired-token", user(1L), LocalDateTime.now().minusMinutes(1), false);

        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(token))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("Refresh token was expired");

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void revokeRefreshToken_shouldMarkTokenAsRevokedAndSaveIt() {
        RefreshToken token = refreshToken("refresh-token", user(1L), LocalDateTime.now().plusDays(1), false);

        refreshTokenService.revokeRefreshToken(token);

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeByToken_shouldRevokeToken_whenTokenExists() {
        RefreshToken token = refreshToken("refresh-token", user(1L), LocalDateTime.now().plusDays(1), false);
        when(refreshTokenRepository.findByToken("refresh-token")).thenReturn(Optional.of(token));

        refreshTokenService.revokeByToken("refresh-token");

        assertThat(token.getRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void revokeByToken_shouldDoNothing_whenTokenDoesNotExist() {
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        refreshTokenService.revokeByToken("unknown-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeAllUserTokens_shouldRevokeAndDeleteRevokedTokensForUser() {
        User user = user(1L);

        refreshTokenService.revokeAllUserTokens(user);

        verify(refreshTokenRepository).revokeAllUserTokens(user);
        verify(refreshTokenRepository).deleteAllRevokedByUser(user);
    }

    @Test
    void deleteExpiredTokens_shouldDeleteAllExpiredTokensUsingCurrentTime() {
        refreshTokenService.deleteExpiredTokens();

        verify(refreshTokenRepository).deleteAllExpiredSince(any(LocalDateTime.class));
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .email("ivan@example.com")
                .username("ivan")
                .passwordHash("encoded-password")
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .isBlocked(false)
                .preferencesJson("{}")
                .build();
    }

    private RefreshToken refreshToken(String token, User user, LocalDateTime expiryDate, boolean revoked) {
        return RefreshToken.builder()
                .id(1L)
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(revoked)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
