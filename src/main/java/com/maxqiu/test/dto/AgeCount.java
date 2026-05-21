package com.maxqiu.test.dto;

import lombok.Data;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@Data
@RegisterReflectionForBinding(AgeCount.class)
public class AgeCount {

    private Integer age;

    private Long count;
}
