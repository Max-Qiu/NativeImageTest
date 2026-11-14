package com.maxqiu.test.compatibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProbeResourceTests {
    @TempDir
    Path directory;

    @Test
    void packagedClasspathResourceIsReadableButCannotBeResolvedAsAWritableFile() throws Exception {
        Path jar = directory.resolve("resources.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("static/probe.txt"));
            output.write("embedded-marker".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (var loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, null)) {
            var resource = new ClassPathResource("static/probe.txt", loader);
            assertThat(resource.getURL().getProtocol()).isEqualTo("jar");
            assertThatThrownBy(resource::getFile).isInstanceOf(FileNotFoundException.class);
            try (var stream = resource.getInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("embedded-marker");
            }
        }
    }

    @Test
    void generatedXmlIsWrittenOutsideClasspathAndCanBeReplacedAndRemoved() throws Exception {
        var store = new ProbeArtifactStore(directory.resolve("external/nested").toString());
        store.write("sample", "first <value> & content");
        assertThat(Files.readString(store.path("sample"))).contains("first &lt;value&gt; &amp; content");
        store.write("sample", "replacement");
        assertThat(Files.readString(store.path("sample"))).contains("replacement").doesNotContain("first");
        store.delete("sample");
        assertThat(Files.exists(store.path("sample"))).isFalse();
    }
}
