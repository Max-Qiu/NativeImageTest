package com.maxqiu.test.compatibility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generated XML belongs in an external writable directory, alongside immutable packaged assets. */
@Component
public class ProbeArtifactStore {
    private final Path directory;

    public ProbeArtifactStore(@Value("${native-probes.output-directory:${java.io.tmpdir}/native-image-probes}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public Path path(String key) {
        if (!key.matches("[A-Za-z0-9-]{1,80}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid fixture key");
        }
        return directory.resolve(key + ".xml");
    }

    public void write(String key, String text) throws Exception {
        Path output = path(key);
        Files.createDirectories(output.getParent());
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var root = document.createElement("probe");
        var value = document.createElement("text");
        value.setTextContent(text);
        root.appendChild(value);
        document.appendChild(root);
        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        try (var stream = Files.newOutputStream(output)) {
            transformer.transform(new DOMSource(document), new StreamResult(stream));
        }
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(path(key));
    }
}
