package com.maxqiu.test.compatibility;

import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class ProbeNativeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(),
                ProbeRow.class, ProbeRow.Phase.class, ProbeController.ProbePage.class,
                ProbeController.CacheSnapshot.class, ProbeController.ArtifactContent.class);
        hints.proxies().registerJdkProxy(ProbeMapper.class);
        hints.reflection().registerType(ProbeMapper.class, MemberCategory.INVOKE_PUBLIC_METHODS);
        hints.resources().registerPattern("db/native-probes.sql");
        hints.resources().registerPattern("static/native-probes/**");
    }
}
