package com.maxqiu.test.controller;

import com.maxqiu.test.service.CacheService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/cache")
public class CacheController {

    private final StringRedisTemplate redis;
    private final CacheService cacheService;

    public CacheController(StringRedisTemplate redis, CacheService cacheService) {
        this.redis = redis;
        this.cacheService = cacheService;
    }

    // ---------- Spring Cache 注解 ----------

    @GetMapping("/anno/load")
    public Map<String, Object> load(@RequestParam String key) {
        String value = cacheService.load(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("hits", cacheService.hitCount());
        return result;
    }

    @PostMapping("/anno/put")
    public Map<String, Object> put(@RequestParam String key, @RequestParam String value) {
        String stored = cacheService.update(key, value);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", stored);
        return result;
    }

    @DeleteMapping("/anno/evict")
    public Map<String, Object> evict(@RequestParam String key) {
        cacheService.evict(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evicted", key);
        return result;
    }

    @DeleteMapping("/anno/evictAll")
    public Map<String, Object> evictAll() {
        cacheService.evictAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evicted", "all");
        return result;
    }

    @PostMapping("/anno/resetHits")
    public Map<String, Object> resetHits() {
        cacheService.resetHitCount();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hits", cacheService.hitCount());
        return result;
    }

    // ---------- StringRedisTemplate 直接操作 ----------

    @GetMapping("/redis/keys")
    public Set<String> keys() {
        Set<String> keys = redis.keys("native-demo:*");
        return keys == null ? new TreeSet<>() : new TreeSet<>(keys);
    }

    @GetMapping("/redis/get")
    public String get(@RequestParam String key) {
        return redis.opsForValue().get(key);
    }

    @PostMapping("/redis/set")
    public Map<String, Object> set(@RequestParam String key,
                                   @RequestParam String value,
                                   @RequestParam(required = false) Long ttlSeconds) {
        if (ttlSeconds != null && ttlSeconds > 0) {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } else {
            redis.opsForValue().set(key, value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("value", value);
        result.put("ttlSeconds", ttlSeconds);
        return result;
    }

    @DeleteMapping("/redis/delete")
    public Map<String, Object> delete(@RequestParam String key) {
        Boolean deleted = redis.delete(key);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("deleted", Boolean.TRUE.equals(deleted));
        return result;
    }
}
