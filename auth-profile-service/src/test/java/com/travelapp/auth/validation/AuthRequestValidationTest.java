package com.travelapp.auth.validation;

import com.travelapp.auth.model.dto.request.ChangePasswordRequest;
import com.travelapp.auth.model.dto.request.LoginRequest;
import com.travelapp.auth.model.dto.request.RefreshTokenRequest;
import com.travelapp.auth.model.dto.request.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void registerRequest_shouldHaveNoViolations_whenDataIsValid() {
        RegisterRequest request = validRegisterRequest();

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void registerRequest_shouldReturnViolation_whenEmailFormatIsInvalid() {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("invalid-email");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
                    assertThat(violation.getMessage()).contains("Invalid email format");
                });
    }

    @Test
    void registerRequest_shouldReturnViolation_whenUsernameIsTooShort() {
        RegisterRequest request = validRegisterRequest();
        request.setUsername("iv");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("username");
                    assertThat(violation.getMessage()).contains("between 3 and 50");
                });
    }

    @Test
    void registerRequest_shouldReturnViolation_whenPasswordIsWeak() {
        RegisterRequest request = validRegisterRequest();
        request.setPassword("password");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
                    assertThat(violation.getMessage()).contains("Пароль должен быть минимум 8 символов");
                });
    }

    @Test
    void loginRequest_shouldReturnViolation_whenEmailIsBlank() {
        LoginRequest request = validLoginRequest();
        request.setEmail("   ");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
                    assertThat(violation.getMessage()).contains("Email is required");
                });
    }

    @Test
    void loginRequest_shouldReturnViolation_whenPasswordIsTooShort() {
        LoginRequest request = validLoginRequest();
        request.setPassword("12345");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
                    assertThat(violation.getMessage()).contains("at least 6");
                });
    }

    @Test
    void refreshTokenRequest_shouldReturnViolation_whenRefreshTokenIsBlank() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(" ");

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("refreshToken");
                    assertThat(violation.getMessage()).contains("Refresh token is required");
                });
    }

    @Test
    void changePasswordRequest_shouldReturnViolation_whenCurrentPasswordIsBlank() {
        ChangePasswordRequest request = validChangePasswordRequest();
        request.setCurrentPassword(" ");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("currentPassword");
                    assertThat(violation.getMessage()).contains("Current password is required");
                });
    }

    @Test
    void changePasswordRequest_shouldReturnViolation_whenNewPasswordIsWeak() {
        ChangePasswordRequest request = validChangePasswordRequest();
        request.setNewPassword("12345678");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("newPassword");
                    assertThat(violation.getMessage()).contains("Пароль должен быть минимум 8 символов");
                });
    }

    @Test
    void strongPasswordValidator_shouldAcceptPasswordWithLettersAndDigits() {
        StrongPasswordValidator validator = new StrongPasswordValidator();

        assertThat(validator.isValid("Password123", null)).isTrue();
    }

    @Test
    void strongPasswordValidator_shouldRejectPasswordWithoutDigits() {
        StrongPasswordValidator validator = new StrongPasswordValidator();

        assertThat(validator.isValid("OnlyLetters", null)).isFalse();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("ivan@example.com");
        request.setUsername("ivan");
        request.setPassword("Password123");
        request.setPhone("+79990000000");
        return request;
    }

    private LoginRequest validLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ivan@example.com");
        request.setPassword("Password123");
        return request;
    }

    private ChangePasswordRequest validChangePasswordRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword123");
        request.setNewPassword("NewPassword123");
        return request;
    }
}
