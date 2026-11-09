package com.maxqiu.test.config;

import com.baomidou.mybatisplus.core.MybatisXMLLanguageDriver;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.override.MybatisMapperProxy;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader;
import org.springframework.jdbc.support.SQLErrorCodes;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/** Dynamic access used by MyBatis-Plus 3.5.x; application types are discovered at build time. */
public class MyBatisNativeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.resources().registerPattern("mapper/*.xml");
        hints.resources().registerPattern("mapper/**/*.xml");
        hints.resources().registerPattern("org/apache/ibatis/builder/xml/*.dtd");
        hints.resources().registerPattern("org/springframework/jdbc/support/sql-error-codes.xml");
        hints.resources().registerPattern("META-INF/spring.schemas");
        hints.resources().registerPattern("org/springframework/beans/factory/xml/*.xsd");
        hints.reflection().registerType(DefaultBeanDefinitionDocumentReader.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        hints.reflection().registerType(SQLErrorCodes.class,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS);

        hints.proxies().registerJdkProxy(SqlSession.class);
        hints.reflection().registerType(SqlSession.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        for (Class<?> mapper : BusinessNativeHints.scanTypes("com.maxqiu.test.mapper", classLoader)) {
            if (mapper.isInterface()) {
                hints.proxies().registerJdkProxy(mapper);
                hints.reflection().registerType(mapper, MemberCategory.INVOKE_PUBLIC_METHODS);
            }
        }

        // Language drivers and the logger are instantiated using reflection.
        for (Class<?> type : new Class<?>[]{MybatisXMLLanguageDriver.class, XMLLanguageDriver.class,
                RawLanguageDriver.class, Slf4jImpl.class}) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }

        // OGNL evaluates wrapper properties in the SQL injected by MyBatis-Plus.
        for (Class<?> type : new Class<?>[]{Wrapper.class, AbstractWrapper.class, QueryWrapper.class,
                LambdaQueryWrapper.class, BaseMapper.class, MybatisMapperProxy.class, IRepository.class}) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_PUBLIC_METHODS);
        }
        hints.reflection().registerType(CrudRepository.class, MemberCategory.ACCESS_DECLARED_FIELDS);

        // Configuration probes these classes by name, even with lazy loading disabled.
        hints.reflection().registerType(TypeReference.of("org.apache.ibatis.javassist.util.proxy.ProxyFactory"));
        hints.reflection().registerType(TypeReference.of("org.apache.ibatis.javassist.ClassPool"));

        // LambdaQueryWrapper falls back to serializing method references in native mode.
        hints.serialization().registerType(java.lang.invoke.SerializedLambda.class);
        hints.serialization().registerType(com.baomidou.mybatisplus.core.toolkit.support.SerializedLambda.class);
    }
}
