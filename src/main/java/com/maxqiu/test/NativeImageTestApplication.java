package com.maxqiu.test;

import com.maxqiu.test.config.MyBatisNativeHints;
import com.maxqiu.test.config.BusinessNativeHints;
import com.maxqiu.test.config.SecuritySessionNativeHints;
import com.maxqiu.test.config.ValidationNativeHints;
import com.maxqiu.test.config.ThymeleafNativeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@EnableCaching
@ImportRuntimeHints({BusinessNativeHints.class, MyBatisNativeHints.class, SecuritySessionNativeHints.class,
        ValidationNativeHints.class, ThymeleafNativeHints.class})
public class NativeImageTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NativeImageTestApplication.class, args);
    }

}
