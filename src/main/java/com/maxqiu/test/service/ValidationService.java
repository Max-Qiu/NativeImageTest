package com.maxqiu.test.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ValidationService {

    public String echo(@NotBlank @Size(min = 3, max = 20) String value) {
        return value;
    }
}
