package com.maxqiu.test;

import com.maxqiu.test.config.BusinessNativeHints;
import com.maxqiu.test.config.MyBatisNativeHints;
import com.maxqiu.test.config.ValidationNativeHints;
import com.maxqiu.test.config.ThymeleafNativeHints;
import com.maxqiu.test.dto.ThymeleafModels;
import com.maxqiu.test.validation.DemoCodeValidator;
import com.maxqiu.test.dto.UnannotatedPayload;
import com.maxqiu.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class NativeHintsTests {

    @Test
    void registersTemplateResourcesExpressionMethodsAndSessionModels() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new ThymeleafNativeHints().registerHints(hints, getClass().getClassLoader());
        new BusinessNativeHints().registerHints(hints, getClass().getClassLoader());

        for (String template : java.util.List.of("text", "conditions", "attributes", "expressions", "fragments",
                "layout", "parts", "tree", "inlining", "context")) {
            assertThat(RuntimeHintsPredicates.resource()
                    .forResource("templates/thymeleaf/" + template + ".html")).accepts(hints);
        }
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(String.class.getMethod("split", String.class))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.Message.class, "getMessage")).accepts(hints);
        assertThat(RuntimeHintsPredicates.serialization()
                .onType(ThymeleafModels.Message.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.Value.class, "getValue")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.NullableValue.class, "getValue")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.Node.class, "getChildren")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.Wrapper.class, "getNested")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(ThymeleafModels.Item.class, "isEnabled")).accepts(hints);
    }

    @Test
    void registersThymeleafJackson3SerializerInitializationMethods() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new ThymeleafNativeHints().registerHints(hints, getClass().getClassLoader());
        Class<?> builder = tools.jackson.databind.json.JsonMapper.Builder.class;
        for (java.lang.reflect.Method method : java.util.List.of(
                tools.jackson.databind.json.JsonMapper.class.getMethod("builder"),
                builder.getMethod("defaultDateFormat", java.text.DateFormat.class),
                builder.getMethod("disable", tools.jackson.databind.SerializationFeature[].class),
                builder.getMethod("findAndAddModules"), builder.getMethod("build"),
                tools.jackson.databind.json.JsonMapper.class.getMethod("writeValue", java.io.Writer.class, Object.class),
                tools.jackson.databind.SerializationFeature.class.getMethod("values"),
                tools.jackson.core.json.JsonWriteFeature.class.getMethod("values"))) {
            assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(method)).accepts(hints);
        }
    }

    @Test
    void registersConstructorsForDtoValidatorsIncludingCustomAndContainerConstraints() throws Exception {
        RuntimeHints hints = new RuntimeHints();
        new ValidationNativeHints().registerHints(hints, getClass().getClassLoader());

        for (Class<?> type : java.util.List.of(DemoCodeValidator.class,
                org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator.class,
                org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator.class,
                org.hibernate.validator.internal.constraintvalidators.bv.NotNullValidator.class,
                org.hibernate.validator.internal.constraintvalidators.bv.notempty.NotEmptyValidatorForCollection.class,
                org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator.class)) {
            assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(type.getDeclaredConstructor()))
                    .accepts(hints);
        }
    }

    @Test
    void discoversNewDtoWithoutAnnotationsAndItsNestedPropertyTypes() {
        RuntimeHints hints = new RuntimeHints();
        new BusinessNativeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(UnannotatedPayload.class, "name")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection()
                .onMethodInvocation(UnannotatedPayload.Detail.class, "value")).accepts(hints);
        assertThat(hints.reflection().getTypeHint(NativeHintsTests.class)).isNull();
    }

    @Test
    void registersMapperAndSessionProxiesAndXmlResources() {
        RuntimeHints hints = new RuntimeHints();
        new MyBatisNativeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(UserMapper.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.proxies().forInterfaces(SqlSession.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("mapper/UserMapper.xml")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource()
                .forResource("org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd")).accepts(hints);
    }

    @Test
    void supportsLambdaWrapperSerializationFallback() {
        RuntimeHints hints = new RuntimeHints();
        new MyBatisNativeHints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.serialization()
                .onType(java.lang.invoke.SerializedLambda.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.serialization()
                .onType(com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda.class))
                .accepts(hints);
    }
}
