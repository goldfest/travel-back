package com.travelapp.auth.controller;

import com.travelapp.auth.model.dto.response.InternalUserResponse;
import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<InternalUserResponse> getCurrentUserInternal() {
        var current = userService.getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(401).build();
        }

        UserResponse user = userService.getUserById(current.getId());
        return ResponseEntity.ok(toInternalResponse(user));
    }

    @GetMapping("/{id}/info")
    public ResponseEntity<InternalUserResponse> getUserInfo(@PathVariable Long id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(toInternalResponse(user));
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> exists(@PathVariable Long id) {
        try {
            userService.getUserById(id);
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }

    private InternalUserResponse toInternalResponse(UserResponse user) {
        return InternalUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(String.valueOf(user.getRole()))
                .status(String.valueOf(user.getStatus()))
                .isBlocked(user.getIsBlocked())
                .avatarUrl(user.getAvatarUrl())
                .homeCityId(user.getHomeCityId())
                .build();
    }
}