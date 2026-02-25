package com.travelapp.city.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class LatValidator implements ConstraintValidator<Lat, BigDecimal> {
    private static final BigDecimal MIN = new BigDecimal("-90");
    private static final BigDecimal MAX = new BigDecimal("90");

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) return true; // null отловит @NotNull
        return value.compareTo(MIN) >= 0 && value.compareTo(MAX) <= 0;
    }
}
