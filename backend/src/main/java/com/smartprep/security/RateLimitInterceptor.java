package com.smartprep.security;

import com.smartprep.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.time.LocalDate;

/**
 * Rate limiter for AI-intensive endpoints.
 * Limits each user to 10 AI requests per minute using Redis-backed token buckets,
 * and 50 AI requests per day using Redis daily counters.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;
    private final StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.capacity:10}")
    private int capacity;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.refill-tokens:10}")
    private int refillTokens;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.refill-duration-minutes:1}")
    private int refillDurationMinutes;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.daily-capacity:50}")
    private int dailyCapacity;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = extractUserId(request);
        if (userId == null) {
            // If we can't identify user, let Spring Security handle auth
            return true;
        }

        // 1. Check daily AI request limit first
        String dailyLimitKey = "ai-limit:daily:" + userId + ":" + LocalDate.now();
        String currentCountStr = redisTemplate.opsForValue().get(dailyLimitKey);
        int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;

        if (currentCount >= dailyCapacity) {
            log.warn("Daily AI limit exceeded for user: {} on path: {}", userId, request.getRequestURI());
            throw new RateLimitExceededException(
                    "You have exceeded your daily AI quota of " + dailyCapacity + " requests. Please try again tomorrow.");
        }

        // 2. Check minute-based rate limit
        String rateLimitKey = "rate-limit:" + userId;

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(refillTokens, Duration.ofMinutes(refillDurationMinutes))
                        .build())
                .build();

        boolean allowed = proxyManager.builder()
                .build(rateLimitKey, () -> config)
                .tryConsume(1);

        if (!allowed) {
            log.warn("Rate limit exceeded for user: {} on path: {}", userId, request.getRequestURI());
            throw new RateLimitExceededException(
                    "Too many AI requests. Please wait a moment before trying again. Limit: " + capacity + " requests per " + refillDurationMinutes + " minute(s).");
        }

        // 3. Increment the daily AI counter upon successful consumption
        Long newCount = redisTemplate.opsForValue().increment(dailyLimitKey);
        if (newCount != null && newCount == 1) {
            redisTemplate.expire(dailyLimitKey, Duration.ofDays(1)); // TTL of 24 hours to clear up old counters
        }

        return true;
    }

    private String extractUserId(HttpServletRequest request) {
        // The JWT filter sets the authenticated principal before this interceptor runs.
        // We extract the user identifier from the security context via the request attribute.
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.smartprep.model.entity.User user) {
            return String.valueOf(user.getUserId());
        }
        return null;
    }
}
