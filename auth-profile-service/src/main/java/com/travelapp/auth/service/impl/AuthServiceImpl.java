package com.travelapp.auth.service.impl;

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
import com.travelapp.auth.service.AuthService;
import com.travelapp.auth.service.RefreshTokenService;
import com.travelapp.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Проверяем существование пользователя
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        // Создаем пользователя
        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .isBlocked(false)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        // Генерируем токены
        String accessToken = jwtService.generateToken(savedUser);
        String refreshToken = refreshTokenService.createRefreshToken(savedUser).getToken();

        // Обновляем время последнего входа
        userService.updateLastLogin(savedUser.getId());

        UserResponse userResponse = userMapper.toResponse(savedUser);

        log.info("User registered successfully: {}", savedUser.getId());
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .user(userResponse)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (Boolean.TRUE.equals(user.getIsBlocked())) {
            throw new UnauthorizedException("User account is blocked");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user).getToken();

        userService.updateLastLogin(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .user(userMapper.toResponse(user))
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Refreshing token");

        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken());

        if (refreshToken == null || refreshToken.getRevoked()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (refreshToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            refreshTokenService.revokeRefreshToken(refreshToken);
            throw new UnauthorizedException("Refresh token expired");
        }

        User user = refreshToken.getUser();

        // Проверяем блокировку и статус
        if (user.getIsBlocked() || user.getStatus() != User.UserStatus.ACTIVE) {
            throw new UnauthorizedException("User account is not active");
        }

        // Отзываем старый refresh token
        refreshTokenService.revokeRefreshToken(refreshToken);

        // Генерируем новые токены
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = refreshTokenService.createRefreshToken(user).getToken();

        log.info("Token refreshed for user: {}", user.getId());
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtService.getJwtExpiration())
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        log.debug("Logging out user");
        refreshTokenService.revokeByToken(refreshToken);
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        log.info("Logging out user from all devices: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        refreshTokenService.revokeAllUserTokens(user);
    }
}