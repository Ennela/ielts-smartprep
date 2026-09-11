package com.smartprep.config;

import com.smartprep.service.MockTestAnalyticsCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig implements CachingConfigurer {

    /**
     * How long a submission's question-level analytics may be served from Redis.
     *
     * <p>The entry is keyed by submission id and status, so it never has to be evicted
     * for the normal GRADING -> COMPLETED transition; what the TTL bounds is the one case
     * that cannot be keyed -- an admin editing a paper's questions after a candidate sat
     * it. An hour is long enough to absorb the repeated opens of a result page and the
     * polling that precedes them, and short enough that a corrected answer key shows up
     * the same afternoon.
     */
    static final Duration ANALYTICS_TTL = Duration.ofHours(1);

    // Only when the cache backend is Redis (the default in every profile but test). The
    // test profile declares spring.cache.type=none, but an unconditional bean here won that
    // contest silently: every @Cacheable call under test reached for a Redis that CI does
    // not run. Stepping aside lets Boot's NoOpCacheManager serve the test profile.
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)) // 24 hours TTL for generated content
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(MockTestAnalyticsCalculator.CACHE_NAME, config.entryTtl(ANALYTICS_TTL))
                .build();
    }

    /**
     * A cache is an accelerator, not a dependency. Without this, a Redis that is down or
     * slow turns every {@code @Cacheable} read into a 500; with it, the failure is logged
     * and the value is simply computed as if there were no cache.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache '{}' get failed for key {}; computing without cache: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache '{}' put failed for key {}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache '{}' evict failed for key {}: {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache '{}' clear failed: {}", cache.getName(), exception.getMessage());
            }
        };
    }
}
