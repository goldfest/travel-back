package com.travelapp.city.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = LngValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Lng {
    String message() default "Долгота должна быть в диапазоне [-180; 180]";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
