package com.travelapp.auth.controller;

import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin management endpoints")
public class AdminController {

    private final UserService userService;

    @GetMapping("/users")
    @Operation(summary = "Get all users (paginated)")
    public ResponseEntity<Page<UserResponse>> getAllUsers(@PageableDefault(size = 20) Pageable pageable) {
        // В реальной реализации нужен сервис для пагинации
        // Для демо возвращаем пустую страницу
        return ResponseEntity.ok(Page.empty());
    }

    @PutMapping("/users/{id}/block")
    @Operation(summary = "Block user")
    public ResponseEntity<UserResponse> blockUser(@PathVariable Long id) {
        UserResponse user = userService.blockUser(id);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/unblock")
    @Operation(summary = "Unblock user")
    public ResponseEntity<UserResponse> unblockUser(@PathVariable Long id) {
        UserResponse user = userService.unblockUser(id);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete user (admin)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }
}