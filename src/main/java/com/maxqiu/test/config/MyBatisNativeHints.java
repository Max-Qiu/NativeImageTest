package com.maxqiu.test.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class MyBatisNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("mapper/*.xml");
        hints.resources().registerPattern("mapper/**/*.xml");
    }
}
