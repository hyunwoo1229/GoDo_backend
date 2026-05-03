package com.example.godo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MediaCacheEvictor {

    @Caching(evict = {
            @CacheEvict(value = "media:gallery", allEntries = true),
            @CacheEvict(value = "media:locations", allEntries = true),
            @CacheEvict(value = "media:byId", key = "#mediaId"),
            @CacheEvict(value = "media:byLocation", allEntries = true)
    })
    public void evictAllForMedia(Long mediaId) {
        log.debug("Evicted media caches for mediaId={}", mediaId);
    }
}
