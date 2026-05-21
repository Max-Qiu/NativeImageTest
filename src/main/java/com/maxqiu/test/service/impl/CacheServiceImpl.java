package com.maxqiu.test.service.impl;

import com.maxqiu.test.service.CacheService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheServiceImpl implements CacheService {

    private final AtomicLong hits = new AtomicLong();

    @Override
    @Cacheable(value = "demo", key = "#key")
    public String load(String key) {
        hits.incrementAndGet();
        return "value-of-" + key + "@" + LocalDateTime.now();
    }

    @Override
    @CachePut(value = "demo", key = "#key")
    public String update(String key, String value) {
        return value + "@" + LocalDateTime.now();
    }

    @Override
    @CacheEvict(value = "demo", key = "#key")
    public void evict(String key) {
    }

    @Override
    @CacheEvict(value = "demo", allEntries = true)
    public void evictAll() {
    }

    @Override
    public long hitCount() {
        return hits.get();
    }

    @Override
    public void resetHitCount() {
        hits.set(0);
    }
}
