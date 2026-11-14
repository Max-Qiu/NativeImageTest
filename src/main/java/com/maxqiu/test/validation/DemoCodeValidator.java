package com.maxqiu.test.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DemoCodeValidator implements ConstraintValidator<DemoCode, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Nullability is handled independently by @NotBlank.
        return value == null || value.matches("DEMO-[A-Z]{3}");
    }
}
