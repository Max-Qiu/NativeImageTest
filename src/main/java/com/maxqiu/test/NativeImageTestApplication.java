package com.maxqiu.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class NativeImageTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeImageTestApplication.class, args);
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from Spring Boot 4 Native Image!";
    }

}
