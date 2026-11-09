package com.maxqiu.test.config;

import java.util.List;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.ClassUtils;

/** Registers business types during AOT processing, without per-type annotations. */
public class BusinessNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
        for (String basePackage : List.of("com.maxqiu.test.entity", "com.maxqiu.test.dto")) {
            for (Class<?> type : scanTypes(basePackage, classLoader)) {
                binding.registerReflectionHints(hints.reflection(), type);
            }
        }
    }

    static List<Class<?>> scanTypes(String basePackage, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                        return definition.getMetadata().isIndependent()
                                && !definition.getMetadata().isAnnotation();
                    }
                };
        scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
        scanner.addIncludeFilter((metadata, factory) -> true);
        return scanner.findCandidateComponents(basePackage).stream()
                .<Class<?>>map(definition -> ClassUtils.resolveClassName(definition.getBeanClassName(), classLoader))
                .sorted(java.util.Comparator.comparing(Class::getName))
                .toList();
    }
}
