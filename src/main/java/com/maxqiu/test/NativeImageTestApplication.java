package com.maxqiu.test;

import com.maxqiu.test.config.MyBatisNativeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(MyBatisNativeHints.class)
public class NativeImageTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeImageTestApplication.class, args);
    }

}
