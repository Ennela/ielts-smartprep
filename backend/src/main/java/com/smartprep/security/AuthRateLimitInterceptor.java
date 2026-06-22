package com.smartprep.security;

import com.smartprep.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * IP-based rate limiter for authentication endpoints.
 * <p>
 * Uses the same Bucket4j + Redis infrastructure as {@link RateLimitInterceptor}
 * but keys on client IP (via {@code X-Forwarded-For}) instead of authenticated user.
 * <p>
 * Two separate policies:
 * <ul>
 *   <li><b>login / forgot-password</b> — {@code auth-login-capacity} requests per minute</li>
 *   <li><b>register</b> — {@code auth-register-capacity} requests per minute (stricter)</li>
 * </ul>
 * <p>
 * This operates at the interceptor layer (before controller), so it is independent
 * of the username-based {@link com.smartprep.service.LoginLockoutService} brute-force
 * lockout which runs at the service layer. Both mechanisms work in parallel.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;

    @Value("${app.rate-limit.auth-login-capacity:10}")
    private int loginCapacity;

    @Value("${app.rate-limit.auth-login-refill-minutes:1}")
    private int loginRefillMinutes;

    @Value("${app.rate-limit.auth-register-capacity:5}")
    private int registerCapacity;

    @Value("${app.rate-limit.auth-register-refill-minutes:1}")
    private int registerRefillMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientIp = resolveClientIp(request);
        String uri = request.getRequestURI();

        // Determine which policy applies based on endpoint
        String endpointKey;
        int capacity;
        int refillMinutes;

        if (uri.endsWith("/register")) {
            endpointKey = "register";
            capacity = registerCapacity;
            refillMinutes = registerRefillMinutes;
        } else {
            // /login and /forgot-password share the same (more lenient) policy
            endpointKey = "login";
            capacity = loginCapacity;
            refillMinutes = loginRefillMinutes;
        }

        String rateLimitKey = "auth-rate-limit:" + endpointKey + ":" + clientIp;

        BucketConfiguration config = BucketConfiguration.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(refillMinutes))
                        .build())
                .build();

        boolean allowed = proxyManager.builder()
                .build(rateLimitKey, () -> config)
                .tryConsume(1);

        if (!allowed) {
            log.warn("Auth rate limit exceeded for IP: {} on endpoint: {}", clientIp, uri);
            throw new RateLimitExceededException(
                    "Too many requests. Please wait before trying again. Limit: "
                            + capacity + " requests per " + refillMinutes + " minute(s).");
        }

        return true;
    }

    /**
     * Resolve client IP from {@code X-Forwarded-For} header (Nginx reverse proxy),
     * falling back to {@code remoteAddr}.
     */
    String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — take the leftmost (original client)
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
