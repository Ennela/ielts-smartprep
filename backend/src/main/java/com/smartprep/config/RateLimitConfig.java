package com.smartprep.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    // Read from the same properties Spring's own Redis client uses. This client is separate
    // -- Bucket4j needs its own codec -- but there is no reason for its timeout to be a
    // different number, and a hardcoded one drifts the moment the shared setting is tuned.
    @Value("${spring.data.redis.timeout:5s}")
    private Duration redisTimeout;

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient() {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort)
                .withTimeout(redisTimeout);
        // Bucket4j gets its own Lettuce client rather than Spring's, so a password set on
        // spring.data.redis reaches Spring's client and not this one. Without this the rate
        // limiter would be the single consumer failing against an authenticated Redis, and
        // it fails closed -- every rate-limited endpoint would start returning errors.
        //
        // The blank check is what keeps that working both ways: an unauthenticated Redis
        // rejects AUTH, so sending an empty password would break the default setup instead.
        if (redisPassword != null && !redisPassword.isBlank()) {
            uri.withPassword(redisPassword.toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean
    public ProxyManager<String> lettuceProxyManager(RedisClient redisClient) {
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
        );
        return LettuceBasedProxyManager.builderFor(connection)
                .build();
    }
}
