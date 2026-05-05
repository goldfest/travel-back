package com.travelapp.auth.service;

import com.travelapp.auth.exception.ResourceNotFoundException;
import com.travelapp.auth.exception.UnauthorizedException;
import com.travelapp.auth.mapper.UserMapper;
import com.travelapp.auth.model.dto.request.ChangePasswordRequest;
import com.travelapp.auth.model.dto.request.UpdateProfileRequest;
import com.travelapp.auth.model.dto.response.UserResponse;
import com.travelapp.auth.model.entity.User;
import com.travelapp.auth.repository.UserRepository;
import com.travelapp.auth.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loadUserByUsername_shouldReturnSpringUserDetails_whenUserExists() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.ADMIN, User.UserStatus.ACTIVE, false);
        when(userRepository.findByEmail("ivan@example.com")).thenReturn(Optional.of(user));

        UserDetails result = userService.loadUserByUsername("ivan@example.com");

        assertThat(result.getUsername()).isEqualTo("ivan@example.com");
        assertThat(result.getPassword()).isEqualTo("encoded-password");
        assertThat(result.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void loadUserByUsername_shouldThrowUsernameNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void registerUser_shouldEncodePasswordSetDefaultsAndSaveUser_whenEmailAndUsernameAreUnique() {
        User user = User.builder()
                .email("ivan@example.com")
                .username("ivan")
                .passwordHash("Password123")
                .role(User.UserRole.USER)
                .build();
        User savedUser = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UserResponse response = userResponse(savedUser);

        when(userRepository.existsByEmail(user.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername(user.getUsername())).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded-password");
        when(userRepository.save(user)).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(response);

        UserResponse result = userService.registerUser(user);

        assertThat(result).isEqualTo(response);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(user.getIsBlocked()).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void registerUser_shouldThrowIllegalArgumentException_whenEmailAlreadyRegistered() {
        User user = user(null, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        when(userRepository.existsByEmail(user.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_shouldReturnUserResponse_whenUserExists() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UserResponse response = userResponse(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.getUserById(1L);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getUserById_shouldThrowResourceNotFoundException_whenUserDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with ID: 404");
    }

    @Test
    void updateUser_shouldUpdateUser_whenCurrentUserIsOwner() {
        User currentUser = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("new_ivan");
        User updatedUser = user(1L, "ivan@example.com", "new_ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UserResponse response = userResponse(updatedUser);

        authenticateAs("ivan@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
        when(userRepository.findByEmailOrUsername("ivan@example.com")).thenReturn(Optional.of(currentUser));
        when(userRepository.save(currentUser)).thenReturn(updatedUser);
        when(userMapper.toResponse(updatedUser)).thenReturn(response);

        UserResponse result = userService.updateUser(1L, request);

        assertThat(result).isEqualTo(response);
        verify(userMapper).updateUserFromRequest(request, currentUser);
        verify(userRepository).save(currentUser);
    }

    @Test
    void updateUser_shouldUpdateAnyUser_whenCurrentUserIsAdmin() {
        User targetUser = user(2L, "user@example.com", "user", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        User admin = user(1L, "admin@example.com", "admin", User.UserRole.ADMIN, User.UserStatus.ACTIVE, false);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setPhone("+79990000001");
        UserResponse response = userResponse(targetUser);

        authenticateAs("admin@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByEmailOrUsername("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.save(targetUser)).thenReturn(targetUser);
        when(userMapper.toResponse(targetUser)).thenReturn(response);

        UserResponse result = userService.updateUser(2L, request);

        assertThat(result).isEqualTo(response);
        verify(userMapper).updateUserFromRequest(request, targetUser);
    }

    @Test
    void updateUser_shouldThrowUnauthorizedException_whenCurrentUserIsNotOwnerAndNotAdmin() {
        User targetUser = user(2L, "user@example.com", "user", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        User currentUser = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("new_user");

        authenticateAs("ivan@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(targetUser));
        when(userRepository.findByEmailOrUsername("ivan@example.com")).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> userService.updateUser(2L, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("permission");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldEncodeAndSaveNewPassword_whenCurrentPasswordIsCorrect() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword123");
        request.setNewPassword("NewPassword123");

        authenticateAs("ivan@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailOrUsername("ivan@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword123", "encoded-password")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("new-encoded-password");

        userService.changePassword(1L, request);

        assertThat(user.getPasswordHash()).isEqualTo("new-encoded-password");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_shouldThrowIllegalArgumentException_whenCurrentPasswordIsIncorrect() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPassword123");
        request.setNewPassword("NewPassword123");

        authenticateAs("ivan@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailOrUsername("ivan@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword123", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateLastLogin_shouldSetLastLoginAndSaveUser_whenUserExists() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.updateLastLogin(1L);

        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void blockUser_shouldSetIsBlockedTrue() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        UserResponse response = userResponse(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.blockUser(1L);

        assertThat(result).isEqualTo(response);
        assertThat(user.getIsBlocked()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void unblockUser_shouldSetIsBlockedFalse() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, true);
        UserResponse response = userResponse(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = userService.unblockUser(1L);

        assertThat(result).isEqualTo(response);
        assertThat(user.getIsBlocked()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_shouldMarkUserAsDeletedAndBlocked() {
        User user = user(1L, "ivan@example.com", "ivan", User.UserRole.USER, User.UserStatus.ACTIVE, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertThat(user.getStatus()).isEqualTo(User.UserStatus.DELETED);
        assertThat(user.getIsBlocked()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void getCurrentUser_shouldReturnNull_whenAuthenticationIsMissing() {
        SecurityContextHolder.clearContext();

        User result = userService.getCurrentUser();

        assertThat(result).isNull();
    }

    @Test
    void isCurrentUserAdmin_shouldReturnTrue_whenCurrentUserHasAdminRole() {
        User admin = user(1L, "admin@example.com", "admin", User.UserRole.ADMIN, User.UserStatus.ACTIVE, false);
        authenticateAs("admin@example.com");
        when(userRepository.findByEmailOrUsername("admin@example.com")).thenReturn(Optional.of(admin));

        boolean result = userService.isCurrentUserAdmin();

        assertThat(result).isTrue();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
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
