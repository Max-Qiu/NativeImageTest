package com.maxqiu.test.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = DemoCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DemoCode {
    String message() default "must match DEMO- followed by three uppercase letters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
