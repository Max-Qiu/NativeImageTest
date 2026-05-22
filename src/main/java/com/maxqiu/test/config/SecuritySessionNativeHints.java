package com.maxqiu.test.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * 为 Spring Session 在 Redis 中 JDK 序列化 SecurityContext / SavedRequest 所需的类型注册反射 + 序列化提示。
 */
public class SecuritySessionNativeHints implements RuntimeHintsRegistrar {

    private static final String[] TYPES = new String[]{
        "org.springframework.security.core.context.SecurityContextImpl",
        "org.springframework.security.authentication.UsernamePasswordAuthenticationToken",
        "org.springframework.security.authentication.AbstractAuthenticationToken",
        "org.springframework.security.core.authority.SimpleGrantedAuthority",
        "org.springframework.security.core.userdetails.User",
        "org.springframework.security.web.authentication.WebAuthenticationDetails",
        "org.springframework.security.web.csrf.DefaultCsrfToken",
        "org.springframework.security.web.savedrequest.DefaultSavedRequest",
        "org.springframework.security.web.savedrequest.SavedCookie",
        "java.util.ArrayList",
        "java.util.TreeSet",
        "java.util.TreeMap",
        "java.util.HashMap",
        "java.util.LinkedHashMap",
        "java.util.Collections$UnmodifiableSet",
        "java.util.Collections$UnmodifiableList",
        "java.util.Collections$UnmodifiableCollection",
        "java.util.Collections$UnmodifiableMap",
        "java.util.Collections$EmptyList",
        "java.util.Collections$EmptySet",
        "java.util.Collections$EmptyMap"
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String type : TYPES) {
            TypeReference ref = TypeReference.of(type);
            hints.serialization().registerType(ref);
            hints.reflection().registerType(ref,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.DECLARED_FIELDS);
        }
    }
}
