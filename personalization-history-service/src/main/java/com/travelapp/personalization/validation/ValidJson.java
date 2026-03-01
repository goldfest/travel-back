package com.travelapp.personalization.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidJsonValidator.class)
@Documented
public @interface ValidJson {
    String message() default "Invalid JSON";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}