package com.maxqiu.test.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeSerialization;

/** Registers serializable method references in the application, including future query lambdas. */
public class NativeLambdaFeature implements Feature {

    private static final String PACKAGE_PATH = "com/maxqiu/test/";

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Set<String> classNames = new TreeSet<>();
        for (Path entry : access.getApplicationClassPath()) {
            collectClassNames(entry, classNames);
        }
        for (String className : classNames) {
            Class<?> type = access.findClassByName(className);
            if (type != null && Arrays.stream(type.getDeclaredMethods())
                    .anyMatch(method -> method.getName().equals("$deserializeLambda$"))) {
                RuntimeSerialization.registerLambdaCapturingClass(type);
            }
        }
    }

    private static void collectClassNames(Path entry, Set<String> classNames) {
        try {
            if (Files.isDirectory(entry)) {
                Path packageDirectory = entry.resolve(PACKAGE_PATH);
                if (Files.isDirectory(packageDirectory)) {
                    try (var paths = Files.walk(packageDirectory)) {
                        paths.filter(Files::isRegularFile)
                                .map(entry::relativize)
                                .map(Path::toString)
                                .map(name -> name.replace('\\', '/'))
                                .filter(name -> name.endsWith(".class"))
                                .map(NativeLambdaFeature::className)
                                .forEach(classNames::add);
                    }
                }
            } else if (entry.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(entry.toFile())) {
                    jar.stream().map(java.util.jar.JarEntry::getName)
                            .filter(name -> name.startsWith(PACKAGE_PATH) && name.endsWith(".class"))
                            .map(NativeLambdaFeature::className)
                            .forEach(classNames::add);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot scan native Lambda capturing classes in " + entry, ex);
        }
    }

    private static String className(String resource) {
        return resource.substring(0, resource.length() - ".class".length()).replace('/', '.');
    }
}
