package com.maxqiu.test.dto;

import java.util.List;

public record ValidationErrorResponse(String error, List<Violation> violations) {
    public record Violation(String field, String message) {}
}
