package com.smartprep.security;

import com.smartprep.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock private ProxyManager<String> proxyManager;
    @Mock private RemoteBucketBuilder<String> bucketBuilder;
    @Mock private io.github.bucket4j.distributed.BucketProxy bucket;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @InjectMocks private RateLimitInterceptor rateLimitInterceptor;

    private com.smartprep.model.entity.User testUser;

    @BeforeEach
    void setUp() {
        testUser = com.smartprep.model.entity.User.builder()
                .userId(123L)
                .username("teststudent")
                .build();

        org.springframework.security.core.Authentication authentication = mock(org.springframework.security.core.Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(testUser);

        org.springframework.security.core.context.SecurityContext securityContext = mock(org.springframework.security.core.context.SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);

        org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Set configuration properties via reflection
        ReflectionTestUtils.setField(rateLimitInterceptor, "capacity", 10);
        ReflectionTestUtils.setField(rateLimitInterceptor, "refillTokens", 10);
        ReflectionTestUtils.setField(rateLimitInterceptor, "refillDurationMinutes", 1);
        ReflectionTestUtils.setField(rateLimitInterceptor, "dailyCapacity", 5);
    }

    @Test
    @DisplayName("should allow request when both daily and minute rate limits are within capacity")
    void preHandle_success() {
        when(valueOps.get(anyString())).thenReturn("2"); // 2 requests today, limit is 5
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(java.util.function.Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(3L);

        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(valueOps).get(startsWith("ai-limit:daily:123:"));
        verify(bucket).tryConsume(1);
        verify(valueOps).increment(startsWith("ai-limit:daily:123:"));
    }

    @Test
    @DisplayName("should block request and throw RateLimitExceededException when daily capacity is exceeded")
    void preHandle_dailyLimitExceeded() {
        when(valueOps.get(anyString())).thenReturn("5"); // 5 requests today, limit is 5

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () ->
                rateLimitInterceptor.preHandle(request, response, new Object())
        );

        assertTrue(exception.getMessage().contains("exceeded your daily AI quota"));
        verify(proxyManager, never()).builder();
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("should block request and throw RateLimitExceededException when minute rate limit is exceeded")
    void preHandle_minuteLimitExceeded() {
        when(valueOps.get(anyString())).thenReturn("0"); // 0 requests today
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(java.util.function.Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(false); // blocked by minute rate limiter

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class, () ->
                rateLimitInterceptor.preHandle(request, response, new Object())
        );

        assertTrue(exception.getMessage().contains("Too many AI requests"));
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("should bypass rate limiting for anonymous requests")
    void preHandle_anonymousRequest() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        boolean result = rateLimitInterceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verifyNoInteractions(redisTemplate, proxyManager);
    }
}
