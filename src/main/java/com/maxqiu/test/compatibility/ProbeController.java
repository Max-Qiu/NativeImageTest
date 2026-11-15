package com.maxqiu.test.compatibility;

import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.graalvm.nativeimage.ImageInfo;
import com.maxqiu.test.NativeImageTestApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

@RestController
@RequestMapping("/probes")
public class ProbeController {
    private final ProbeService service;
    private final ProbeCache cache;
    private final ProbeArtifactStore artifacts;

    public ProbeController(ProbeService service, ProbeCache cache, ProbeArtifactStore artifacts) {
        this.service = service;
        this.cache = cache;
        this.artifacts = artifacts;
    }

    @GetMapping("/environment")
    public Map<String, Object> environment() {
        return Map.of("nativeRuntime", ImageInfo.inImageRuntimeCode(),
                "application", NativeImageTestApplication.class.getName());
    }

    @GetMapping("/collections")
    public Map<String, String> collections() {
        DefaultObjectFactory factory = new DefaultObjectFactory();
        return Map.of("list", factory.create(List.class).getClass().getName(),
                "map", factory.create(Map.class).getClass().getName(),
                "set", factory.create(Set.class).getClass().getName(),
                "sortedSet", factory.create(SortedSet.class).getClass().getName());
    }

    @PostMapping("/datasets/{key}")
    public List<ProbeRow> seed(@PathVariable String key) {
        cache.evict(key);
        return service.seed(key);
    }

    @GetMapping("/datasets/{key}/page")
    public ProbePage page(@PathVariable String key, @RequestParam(defaultValue = "1") long page,
                          @RequestParam(defaultValue = "2") long size, @RequestParam(required = false) ProbeRow.Phase phase) {
        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page bounds");
        }
        var result = service.page(key, page, size, phase);
        return new ProbePage(result.getCurrent(), result.getSize(), result.getTotal(), result.getPages(), result.getRecords());
    }

    @GetMapping("/datasets/{key}/codes")
    public List<Integer> codes(@PathVariable String key) {
        return service.storedCodes(key);
    }

    @GetMapping("/datasets/{key}/cache")
    public CacheSnapshot cached(@PathVariable String key) {
        List<ProbeRow> rows = cache.snapshot(key);
        String phaseType = rows.isEmpty() ? "empty" : rows.getFirst().getPhase().getClass().getName();
        return new CacheSnapshot(rows, cache.loads(key), rows.getClass().getName(), phaseType);
    }

    @PostMapping("/datasets/{key}/first-phase")
    public void changeFirstPhase(@PathVariable String key, @RequestParam ProbeRow.Phase phase) {
        service.changeFirstPhase(key, phase);
    }

    @DeleteMapping("/datasets/{key}/cache")
    public void evict(@PathVariable String key) {
        cache.evict(key);
    }

    @DeleteMapping("/datasets/{key}")
    public void delete(@PathVariable String key) {
        cache.evict(key);
        service.delete(key);
    }

    @DeleteMapping("/datasets/{key}/batch")
    public int deleteFirstPairByIds(@PathVariable String key) {
        cache.evict(key);
        return service.deleteFirstPairByIds(key);
    }

    @GetMapping("/resources")
    public Map<String, Object> resources() throws Exception {
        var resource = new ClassPathResource("static/native-probes/sample.txt");
        boolean fileResolved;
        try {
            resource.getFile();
            fileResolved = true;
        } catch (FileNotFoundException expected) {
            fileResolved = false;
        }
        try (var stream = resource.getInputStream()) {
            return Map.of("protocol", resource.getURL().getProtocol(), "fileResolved", fileResolved,
                    "content", new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip());
        }
    }

    @PostMapping("/artifacts/{key}")
    public Map<String, String> generate(@PathVariable String key, @RequestBody ArtifactContent content) throws Exception {
        artifacts.write(key, content.text());
        return Map.of("location", artifacts.path(key).toString());
    }

    @GetMapping(value = "/artifacts/{key}", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<Resource> artifact(@PathVariable String key) {
        Resource resource = new FileSystemResource(artifacts.path(key));
        return resource.isReadable() ? ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(resource)
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/artifacts/{key}")
    public void deleteArtifact(@PathVariable String key) throws Exception {
        artifacts.delete(key);
    }

    public record ProbePage(long current, long size, long total, long pages, List<ProbeRow> records) {
    }

    public record CacheSnapshot(List<ProbeRow> rows, int loads, String listType, String phaseType) {
    }

    public record ArtifactContent(String text) {
    }
}
