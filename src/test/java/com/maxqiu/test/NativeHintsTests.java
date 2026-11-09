package com.maxqiu.test;

import com.maxqiu.test.config.BusinessNativeHints;
import com.maxqiu.test.config.MyBatisNativeHints;
import com.maxqiu.test.dto.UnannotatedPayload;
import com.maxqiu.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import static org.assertj.core.api.Assertions.assertThat;

class NativeHintsTests {

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
