package com.rutatur.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Validates that a string matches one of a closed enum's constant names. */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> value();

    String message() default "must be one of the supported values";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
