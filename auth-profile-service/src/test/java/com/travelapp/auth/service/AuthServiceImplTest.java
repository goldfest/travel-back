package com.travelapp.auth.service;

import com.travelapp.auth.exception.UnauthorizedException;
import com.travelapp.auth.mapper.UserMapper;
import com.travelapp.auth.model.dto.request.LoginRequest;
import com.travelapp.auth.model.dto.request.RefreshTokenRequest;
import com.travelapp.auth.model.dto.request.RegisterRequest;
import com.travelapp.auth.model.dto.response.AuthResponse;
import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.model.entity.RefreshToken;
import com.travelapp.auth.model.entity.User;
import com.travelapp.auth.repository.UserRepository;
import com.travelapp.auth.security.jwt.JwtService;
import com.travelapp.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthServiceImpl authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_shouldCreateActiveUserAndReturnTokens_whenEmailAndUsernameAreUnique() {
        RegisterRequest request = registerRequest();
        User savedUser = user(1L, request.getEmail(), request.getUsername(), User.UserRole.USER, User.UserStatus.ACTIVE, false);
        RefreshToken refreshToken = refreshToken("refresh-token", savedUser, LocalDateTime.now().plusDays(30), false);
        UserResponse userResponse = userResponse(savedUser);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(savedUser)).thenReturn(refreshToken);
        when(jwtService.getJwtExpiration()).thenReturn(3600000L);
        when(userMapper.toResponse(savedUser)).thenReturn(userResponse);

        AuthResponse result = authService.register(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getExpiresIn()).isEqualTo(3600000L);
        assertThat(result.getUser()).isEqualTo(userResponse);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User userToSave = userCaptor.getValue();
        assertThat(userToSave.getEmail()).isEqualTo(request.getEmail());
        assertThat(userToSave.getUsername()).isEqualTo(request.getUsername());
        assertThat(userToSave.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userToSave.getRole()).isEqualTo(User.UserRole.USER);
        assertThat(userToSave.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(userToSave.getIsBlocked()).isFalse();
        assertThat(userToSave.getPreferencesJson()).isEqualTo("{}");

        verify(userService).updateLastLogin(savedUser.getId());
    }

    @Test
    void register_shouldThrowIllegalArgumentException_whenEmailAlreadyUsed() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Почта уже используется");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void register_shouldThrowIllegalArgumentException_whenUsernameAlreadyUsed() {
        RegisterRequest request = registerRequest();
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Логин уже используется");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void login_shouldAuthenticateAndReturnTokens_whenUserIsActive() {
        LoginRequest request = loginRequest();
        User user = user(1L, request.getEmail(), "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        RefreshToken refreshToken = refreshToken("refresh-token", user, LocalDateTime.now().plusDays(30), false);
        UserResponse userResponse = userResponse(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(request.getEmail(), null, List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshToken);
        when(jwtService.getJwtExpiration()).thenReturn(3600000L);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        AuthResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(result.getUser()).isEqualTo(userResponse);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(authentication);
        verify(userService).updateLastLogin(user.getId());
    }

    @Test
    void login_shouldThrowUnauthorizedException_whenUserIsBlocked() {
        LoginRequest request = loginRequest();
        User user = user(1L, request.getEmail(), "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, true);
        Authentication authentication = new UsernamePasswordAuthenticationToken(request.getEmail(), null, List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Пользователь заблокирован");

        verify(jwtService, never()).generateToken(any(User.class));
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void login_shouldThrowUnauthorizedException_whenUserStatusIsNotActive() {
        LoginRequest request = loginRequest();
        User user = user(1L, request.getEmail(), "ivan", User.UserRole.USER, User.UserStatus.SUSPENDED, false);
        Authentication authentication = new UsernamePasswordAuthenticationToken(request.getEmail(), null, List.of());

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Аккаунт не активен");

        verify(jwtService, never()).generateToken(any(User.class));
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void refreshToken_shouldRevokeOldTokenAndReturnNewTokens_whenTokenIsValid() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        RefreshToken oldToken = refreshToken("old-refresh", user, LocalDateTime.now().plusDays(1), false);
        RefreshToken newToken = refreshToken("new-refresh", user, LocalDateTime.now().plusDays(30), false);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh");

        when(refreshTokenService.findByToken("old-refresh")).thenReturn(oldToken);
        when(jwtService.generateToken(user)).thenReturn("new-access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(newToken);
        when(jwtService.getJwtExpiration()).thenReturn(3600000L);

        AuthResponse result = authService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(result.getExpiresIn()).isEqualTo(3600000L);
        assertThat(result.getUser()).isNull();
        verify(refreshTokenService).revokeRefreshToken(oldToken);
    }

    @Test
    void refreshToken_shouldThrowUnauthorizedException_whenTokenNotFound() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("unknown-token");
        when(refreshTokenService.findByToken("unknown-token")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");

        verify(refreshTokenService, never()).revokeRefreshToken(any());
    }

    @Test
    void refreshToken_shouldThrowUnauthorizedException_whenTokenIsRevoked() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        RefreshToken revokedToken = refreshToken("revoked-refresh", user, LocalDateTime.now().plusDays(1), true);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("revoked-refresh");

        when(refreshTokenService.findByToken("revoked-refresh")).thenReturn(revokedToken);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");

        verify(refreshTokenService, never()).revokeRefreshToken(any());
    }

    @Test
    void refreshToken_shouldRevokeAndThrowUnauthorizedException_whenTokenIsExpired() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        RefreshToken expiredToken = refreshToken("expired-refresh", user, LocalDateTime.now().minusMinutes(1), false);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-refresh");

        when(refreshTokenService.findByToken("expired-refresh")).thenReturn(expiredToken);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Refresh token expired");

        verify(refreshTokenService).revokeRefreshToken(expiredToken);
    }

    @Test
    void refreshToken_shouldThrowUnauthorizedException_whenUserIsBlocked() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, true);
        RefreshToken token = refreshToken("refresh-token", user, LocalDateTime.now().plusDays(1), false);
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        when(refreshTokenService.findByToken("refresh-token")).thenReturn(token);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User account is not active");

        verify(refreshTokenService, never()).revokeRefreshToken(token);
        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    void logout_shouldRevokeRefreshTokenByTokenValue() {
        authService.logout("refresh-token");

        verify(refreshTokenService).revokeByToken("refresh-token");
    }

    @Test
    void logoutAll_shouldRevokeAllUserTokens_whenUserExists() {
        Long userId = 1L;
        User user = user(userId, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        authService.logoutAll(userId);

        verify(refreshTokenService).revokeAllUserTokens(user);
    }

    @Test
    void logoutAll_shouldThrowIllegalArgumentException_whenUserDoesNotExist() {
        Long userId = 404L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.logoutAll(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(refreshTokenService, never()).revokeAllUserTokens(any());
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("ivan@example.com");
        request.setUsername("ivan");
        request.setPassword("Password123");
        request.setPhone("+79990000000");
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ivan@example.com");
        request.setPassword("Password123");
        return request;
    }

    private User user(Long id, String email, String username, User.UserRole role, User.UserStatus status, boolean blocked) {
        return User.builder()
                .id(id)
                .email(email)
                .username(username)
                .passwordHash("encoded-password")
                .phone("+79990000000")
                .preferencesJson("{}")
                .role(role)
                .status(status)
                .isBlocked(blocked)
                .build();
    }

    private RefreshToken refreshToken(String token, User user, LocalDateTime expiryDate, boolean revoked) {
        return RefreshToken.builder()
                .id(10L)
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .revoked(revoked)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UserResponse userResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .isBlocked(user.getIsBlocked())
                .preferencesJson(user.getPreferencesJson())
                .build();
    }
}
