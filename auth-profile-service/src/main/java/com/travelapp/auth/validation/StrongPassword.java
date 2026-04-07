package com.travelapp.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Пароль должен быть минимум 8 символов и содержать буквы и цифры";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}