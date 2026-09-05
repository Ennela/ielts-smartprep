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
     * Resolve the client IP used as the rate-limit key, falling back to
     * {@code remoteAddr} when no {@code X-Forwarded-For} is present.
     * <p>
     * Takes the <em>rightmost</em> entry, not the leftmost. A caller can send its own
     * {@code X-Forwarded-For}, and an appending proxy keeps that value and adds the real
     * peer after it — so the leftmost entry is attacker-chosen. Reading it meant one host
     * could vary the header per request and never exhaust a bucket. The rightmost entry is
     * written by the proxy closest to this application and is the only one a caller cannot
     * influence.
     * <p>
     * This assumes exactly one trusted proxy in front of the application, which is what
     * {@code frontend/nginx.conf} sets up; it also overwrites the header rather than
     * appending, so in the deployed topology there is only ever one entry. Exposing the
     * backend port directly to untrusted clients would defeat both measures.
     */
    String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            String nearest = hops[hops.length - 1].trim();
            if (!nearest.isEmpty()) {
                return nearest;
            }
        }
        return request.getRemoteAddr();
    }
}
