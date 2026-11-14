package com.maxqiu.test.controller;

import com.maxqiu.test.dto.ValidationRequest;
import com.maxqiu.test.service.ValidationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/validation")
public class ValidationController {

    private final ValidationService validationService;

    public ValidationController(ValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/body")
    public ValidationRequest body(@Valid @RequestBody ValidationRequest request) {
        return request;
    }

    @PutMapping("/body")
    public ValidationRequest update(@Validated(ValidationRequest.Update.class)
                                    @RequestBody ValidationRequest request) {
        return request;
    }

    // Use MVC method validation, without class-level @Validated on the controller.
    @GetMapping("/parameters")
    public Map<String, Integer> parameters(@RequestParam @Min(1) @Max(10) int count) {
        return Map.of("count", count);
    }

    @GetMapping("/service")
    public Map<String, String> service(@RequestParam String value) {
        return Map.of("value", validationService.echo(value));
    }
}
