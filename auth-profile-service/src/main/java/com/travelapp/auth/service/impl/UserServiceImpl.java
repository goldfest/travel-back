package com.travelapp.auth.service.impl;

import com.travelapp.auth.exception.ResourceNotFoundException;
import com.travelapp.auth.exception.UnauthorizedException;
import com.travelapp.auth.mapper.UserMapper;
import com.travelapp.auth.model.dto.request.ChangePasswordRequest;
import com.travelapp.auth.model.dto.request.UpdateProfileRequest;
import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.model.entity.User;
import com.travelapp.auth.repository.UserRepository;
import com.travelapp.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmailOrUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(user.getIsBlocked() || user.getStatus() != User.UserStatus.ACTIVE)
                .accountExpired(false)
                .accountLocked(user.getIsBlocked())
                .credentialsExpired(false)
                .build();
    }

    @Override
    @Transactional
    public UserResponse registerUser(User user) {
        log.info("Registering new user with email: {}", user.getEmail());

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setIsBlocked(false);
        user.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with ID: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user by ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateProfileRequest request) {
        log.info("Updating user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Проверяем права доступа
        checkUserPermission(user);

        userMapper.updateUserFromRequest(request, user);
        User updatedUser = userRepository.save(user);

        log.info("User updated successfully: {}", userId);
        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        log.info("Changing password for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Проверяем права доступа
        checkUserPermission(user);

        // Проверяем текущий пароль
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Обновляем пароль
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed successfully for user ID: {}", userId);
    }

    @Override
    @Transactional
    public void updateLastLogin(Long userId) {
        log.debug("Updating last login for user ID: {}", userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Override
    @Transactional
    public UserResponse blockUser(Long userId) {
        log.info("Blocking user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setIsBlocked(true);
        User blockedUser = userRepository.save(user);

        log.info("User blocked successfully: {}", userId);
        return userMapper.toResponse(blockedUser);
    }

    @Override
    @Transactional
    public UserResponse unblockUser(Long userId) {
        log.info("Unblocking user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        user.setIsBlocked(false);
        User unblockedUser = userRepository.save(user);

        log.info("User unblocked successfully: {}", userId);
        return userMapper.toResponse(unblockedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user with ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Мягкое удаление - меняем статус
        user.setStatus(User.UserStatus.DELETED);
        user.setIsBlocked(true);
        userRepository.save(user);

        log.info("User marked as deleted: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public com.travelapp.auth.model.entity.User getCurrentUser() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByEmailOrUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    @Override
    public boolean isCurrentUserAdmin() {
        User user = getCurrentUser();
        return user != null && user.getRole() == User.UserRole.ADMIN;
    }

    private void checkUserPermission(User user) {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            throw new UnauthorizedException("User not authenticated");
        }

        // Админ может редактировать любого пользователя
        if (currentUser.getRole() == User.UserRole.ADMIN) {
            return;
        }

        // Пользователь может редактировать только свой профиль
        if (!currentUser.getId().equals(user.getId())) {
            throw new UnauthorizedException("You don't have permission to modify this user");
        }
    }
}