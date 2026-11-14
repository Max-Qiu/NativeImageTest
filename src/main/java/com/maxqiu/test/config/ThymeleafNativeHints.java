package com.maxqiu.test.config;

import com.maxqiu.test.dto.ThymeleafModels;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.Writer;
import java.text.DateFormat;

/** Application resources and reflective calls made only from template expressions. */
public class ThymeleafNativeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("templates/thymeleaf/**");
        hints.serialization().registerType(ThymeleafModels.Message.class);
        try {
            hints.reflection().registerMethod(String.class.getMethod("split", String.class), ExecutableMode.INVOKE);

            // Thymeleaf 3.1.5 initializes its Jackson 3 serializer reflectively,
            // even for pages that only use text inlining. The dependency metadata
            // currently covers the earlier Jackson 2 initialization path.
            hints.reflection().registerMethod(JsonMapper.class.getMethod("builder"), ExecutableMode.INVOKE);
            hints.reflection().registerMethod(JsonMapper.Builder.class.getMethod("defaultDateFormat", DateFormat.class),
                    ExecutableMode.INVOKE);
            hints.reflection().registerMethod(JsonMapper.Builder.class.getMethod("disable", SerializationFeature[].class),
                    ExecutableMode.INVOKE);
            hints.reflection().registerMethod(JsonMapper.Builder.class.getMethod("findAndAddModules"), ExecutableMode.INVOKE);
            hints.reflection().registerMethod(JsonMapper.Builder.class.getMethod("build"), ExecutableMode.INVOKE);
            hints.reflection().registerMethod(JsonMapper.class.getMethod("writeValue", Writer.class, Object.class),
                    ExecutableMode.INVOKE);
            hints.reflection().registerType(SerializationFeature.class, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(JsonWriteFeature.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Cannot register template expression methods", exception);
        }
    }
}
