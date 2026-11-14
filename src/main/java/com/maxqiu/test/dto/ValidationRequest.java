package com.maxqiu.test.dto;

import com.maxqiu.test.validation.DemoCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;
import lombok.Data;

import java.util.List;

@Data
public class ValidationRequest {

    // Inherit Default so an update also checks all ordinary field constraints.
    public interface Update extends Default {}

    @NotNull(groups = Update.class)
    private Long id;

    @NotBlank
    @Size(max = 40)
    private String name;

    @NotBlank
    @Email
    private String email;

    @NotNull
    @Min(18)
    @Max(120)
    private Integer age;

    @NotNull
    @Valid
    private Address address;

    @NotEmpty
    private List<@NotBlank String> tags;

    @NotBlank
    @DemoCode
    private String code;

    @Data
    public static class Address {
        @NotBlank
        private String city;

        @NotBlank
        @Pattern(regexp = "\\d{6}")
        private String zipCode;
    }
}
