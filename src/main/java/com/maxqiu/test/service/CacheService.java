package com.maxqiu.test.service;

public interface CacheService {

    String load(String key);

    String update(String key, String value);

    void evict(String key);

    void evictAll();

    long hitCount();

    void resetHitCount();
}
