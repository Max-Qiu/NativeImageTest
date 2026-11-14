package com.maxqiu.test.compatibility;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ProbeCache {
    private final ProbeService service;
    private final ConcurrentHashMap<String, AtomicInteger> loads = new ConcurrentHashMap<>();

    public ProbeCache(ProbeService service) {
        this.service = service;
    }

    @Cacheable(cacheNames = "probe-enum-list", key = "#key")
    public List<ProbeRow> snapshot(String key) {
        loads.computeIfAbsent(key, _ -> new AtomicInteger()).incrementAndGet();
        return service.list(key);
    }

    public int loads(String key) {
        return loads.getOrDefault(key, new AtomicInteger()).get();
    }

    @CacheEvict(cacheNames = "probe-enum-list", key = "#key")
    public void evict(String key) {
    }
}
