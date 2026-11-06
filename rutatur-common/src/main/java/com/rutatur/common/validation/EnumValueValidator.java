package com.rutatur.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;

/** Null is considered valid so that {@code @NotNull} owns presence checks. */
public class EnumValueValidator implements ConstraintValidator<EnumValue, String> {

    private String[] allowed;

    @Override
    public void initialize(EnumValue annotation) {
        allowed = Arrays.stream(annotation.value().getEnumConstants())
                .map(Enum::name)
                .toArray(String[]::new);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return Arrays.asList(allowed).contains(value);
    }
}
