package com.maxqiu.test.config;

import jakarta.validation.Validation;
import jakarta.validation.metadata.ConstraintDescriptor;
import jakarta.validation.metadata.ContainerDescriptor;
import jakarta.validation.metadata.ElementDescriptor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.List;

/** Discovers validators on business types that are not themselves Spring beans. */
public class ValidationNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String basePackage : List.of("com.maxqiu.test.entity", "com.maxqiu.test.dto")) {
                for (Class<?> type : BusinessNativeHints.scanTypes(basePackage, classLoader)) {
                    var descriptor = validator.getConstraintsForClass(type);
                    registerElement(hints, descriptor);
                    descriptor.getConstrainedProperties().forEach(property -> registerElement(hints, property));
                }
            }
        }
    }

    private void registerElement(RuntimeHints hints, ElementDescriptor descriptor) {
        descriptor.getConstraintDescriptors().forEach(constraint -> registerConstraint(hints, constraint));
        if (descriptor instanceof ContainerDescriptor container) {
            container.getConstrainedContainerElementTypes().forEach(element -> registerElement(hints, element));
        }
    }

    private void registerConstraint(RuntimeHints hints, ConstraintDescriptor<?> descriptor) {
        descriptor.getConstraintValidatorClasses().forEach(type ->
                hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        descriptor.getComposingConstraints().forEach(constraint -> registerConstraint(hints, constraint));
    }
}
