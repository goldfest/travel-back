package com.travelapp.auth.service;

import com.travelapp.auth.model.dto.request.ChangePasswordRequest;
import com.travelapp.auth.model.dto.request.UpdateProfileRequest;
import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.model.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface UserService extends UserDetailsService {

    UserResponse registerUser(User user);

    UserResponse getUserById(Long id);

    UserResponse getUserByEmail(String email);

    UserResponse updateUser(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    void updateLastLogin(Long userId);

    UserResponse blockUser(Long userId);

    UserResponse unblockUser(Long userId);

    void deleteUser(Long userId);

    User getCurrentUser();

    boolean isCurrentUserAdmin();
}