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

import java.time.Duration;

/**
 * Rate limiter for AI-intensive endpoints.
 * Limits each user to 10 AI requests per minute using Redis-backed token buckets.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.capacity:10}")
    private int capacity;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.refill-tokens:10}")
    private int refillTokens;

    @org.springframework.beans.factory.annotation.Value("${app.rate-limit.refill-duration-minutes:1}")
    private int refillDurationMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = extractUserId(request);
        if (userId == null) {
            // If we can't identify user, let Spring Security handle auth
            return true;
        }

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

        if (allowed) {
            return true;
        }

        log.warn("Rate limit exceeded for user: {} on path: {}", userId, request.getRequestURI());
        throw new RateLimitExceededException(
                "Too many AI requests. Please wait a moment before trying again. Limit: " + capacity + " requests per " + refillDurationMinutes + " minute(s).");
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
