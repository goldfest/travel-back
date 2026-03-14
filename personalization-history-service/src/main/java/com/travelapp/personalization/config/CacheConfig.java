package com.travelapp.personalization.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()
                        )
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                );

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();

        configs.put("favorites", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));
        configs.put("favoriteCheck", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));
        configs.put("favoriteCount", defaultConfig.entryTtl(Duration.ofSeconds(favoritesTtl)));

        configs.put("collections", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collection", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collectionSearch", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));
        configs.put("collectionPois", defaultConfig.entryTtl(Duration.ofSeconds(collectionsTtl)));

        configs.put("searchHistory", defaultConfig.entryTtl(Duration.ofSeconds(searchHistoryTtl)));
        configs.put("recentQueries", defaultConfig.entryTtl(Duration.ofSeconds(searchHistoryTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(configs)
                .build();
    }
}