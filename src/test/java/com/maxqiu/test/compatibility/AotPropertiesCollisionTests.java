package com.maxqiu.test.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.aot.AbstractAotProcessor;
import org.springframework.context.aot.ContextAotProcessor;
import org.springframework.context.support.GenericApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

class AotPropertiesCollisionTests {
    private static final String FEATURE = "Args = --features=com.maxqiu.test.config.NativeLambdaFeature\n";

    @TempDir
    Path directory;

    @Test
    void reproducesLossOfCustomFeatureWhenAotOutputIsCopiedToTheSameCoordinates() throws Exception {
        Path resources = directory.resolve("application-resources");
        Path samePath = resources.resolve("META-INF/native-image/example/probe/native-image.properties");
        Files.createDirectories(samePath.getParent());
        Files.writeString(samePath, FEATURE);

        copyGeneratedProperties(resources);

        assertThat(Files.readString(samePath)).contains("MinimalApplication").doesNotContain("--features=");
    }

    @Test
    void separateFeatureDirectorySurvivesTheRealAotGeneratedResource() throws Exception {
        Path resources = directory.resolve("application-resources");
        Path separate = resources.resolve("META-INF/native-image/example/probe-features/native-image.properties");
        Files.createDirectories(separate.getParent());
        Files.writeString(separate, FEATURE);

        copyGeneratedProperties(resources);

        assertThat(Files.readString(separate)).isEqualTo(FEATURE);
        assertThat(Files.readString(resources.resolve("META-INF/native-image/example/probe/native-image.properties")))
                .contains("MinimalApplication");
    }

    private void copyGeneratedProperties(Path applicationResources) throws Exception {
        Path generated = directory.resolve("generated-resources");
        var settings = AbstractAotProcessor.Settings.builder()
                .sourceOutput(directory.resolve("generated-sources"))
                .resourceOutput(generated).classOutput(directory.resolve("generated-classes"))
                .groupId("example").artifactId("probe").build();
        new ContextAotProcessor(MinimalApplication.class, settings) {
            @Override
            protected GenericApplicationContext prepareApplicationContext(Class<?> application) {
                return new GenericApplicationContext();
            }
        }.process();
        // Maven copies AOT output into application classes after copying source resources.
        Path relative = Path.of("META-INF/native-image/example/probe/native-image.properties");
        Path destination = applicationResources.resolve(relative);
        Files.createDirectories(destination.getParent());
        Files.copy(generated.resolve(relative), destination, StandardCopyOption.REPLACE_EXISTING);
    }

    public static class MinimalApplication {
        public static void main(String[] args) {
        }
    }
}
