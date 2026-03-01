package com.travelapp.personalization.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${app.cache.favorites.ttl:1800}")
    private long favoritesTtl;

    @Value("${app.cache.collections.ttl:3600}")
    private long collectionsTtl;

    @Value("${app.cache.search-history.ttl:900}")
    private long searchHistoryTtl;

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        // favorites
        configs.put("favorites", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));
        configs.put("favoriteCheck", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));
        configs.put("favoriteCount", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));

        // collections
        configs.put("collections", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collection", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collectionSearch", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collectionPois", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));

        // search history
        configs.put("searchHistory", defaultConfig.entryTtl(Duration.ofSeconds(searchHistoryTtl)));
        configs.put("recentQueries", defaultConfig.entryTtl(Duration.ofSeconds(searchHistoryTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}