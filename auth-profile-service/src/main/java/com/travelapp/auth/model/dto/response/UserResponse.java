package com.travelapp.auth.model.dto.response;

import com.travelapp.auth.model.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String username;
    private String phone;
    private String avatarUrl;
    private User.UserRole role;
    private User.UserStatus status;
    private Boolean isBlocked;
    private LocalDateTime lastLoginAt;
    private Long homeCityId;
    private String preferencesJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}