package com.maxqiu.test.dto;

import java.util.List;

// No Spring or native annotations: package scanning must discover this new DTO.
public record UnannotatedPayload(String name, List<Detail> details) {
    public record Detail(String value) {}
}
