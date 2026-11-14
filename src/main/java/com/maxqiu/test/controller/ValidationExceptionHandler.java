package com.maxqiu.test.controller;

import com.maxqiu.test.dto.ValidationErrorResponse;
import com.maxqiu.test.dto.ValidationErrorResponse.Violation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Comparator;
import java.util.stream.Stream;

@RestControllerAdvice(assignableTypes = ValidationController.class)
public class ValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse body(MethodArgumentNotValidException exception) {
        return response(exception.getBindingResult().getAllErrors().stream()
                .map(error -> new Violation(error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        error.getDefaultMessage())));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse parameters(HandlerMethodValidationException exception) {
        return response(exception.getParameterValidationResults().stream().flatMap(result -> {
            String name = result.getMethodParameter().getParameterName();
            String field = name != null ? name : "arg" + result.getMethodParameter().getParameterIndex();
            return result.getResolvableErrors().stream()
                    .map(error -> new Violation(field, error.getDefaultMessage()));
        }));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse service(ConstraintViolationException exception) {
        return response(exception.getConstraintViolations().stream()
                .map(violation -> new Violation(violation.getPropertyPath().toString(), violation.getMessage())));
    }

    private ValidationErrorResponse response(Stream<Violation> violations) {
        return new ValidationErrorResponse("VALIDATION_FAILED", violations.distinct()
                .sorted(Comparator.comparing(Violation::field).thenComparing(Violation::message)).toList());
    }
}
