package com.smartprep.security;

import com.smartprep.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for AI-intensive endpoints.
 * Limits each user to 10 AI requests per minute using in-memory token buckets.
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    private final ConcurrentHashMap<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = extractUserId(request);
        if (userId == null) {
            // If we can't identify user, let Spring Security handle auth
            return true;
        }

        Bucket bucket = bucketCache.computeIfAbsent(userId, this::createBucket);

        if (bucket.tryConsume(1)) {
            return true;
        }

        log.warn("Rate limit exceeded for user: {} on path: {}", userId, request.getRequestURI());
        throw new RateLimitExceededException(
                "Too many AI requests. Please wait a moment before trying again. Limit: 10 requests per minute.");
    }

    private Bucket createBucket(String userId) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(10)
                .refillIntervally(10, Duration.ofMinutes(1))
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
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
