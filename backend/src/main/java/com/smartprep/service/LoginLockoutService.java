package com.smartprep.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Tracks failed login attempts per username in Redis and enforces temporary lockout.
 * <p>
 * Redis key: {@code login-fail:{username}} → fail count (TTL = lockout duration).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoginLockoutService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.max-login-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    private static final String FAIL_PREFIX = "login-fail:";

    /**
     * Record a failed login attempt. If this is the first attempt, set TTL to lockout duration.
     */
    public void recordFailedAttempt(String username) {
        String key = FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(key);

        // Set TTL only on first failure (when count transitions to 1)
        if (count != null && count == 1) {
            redisTemplate.expire(key, lockoutDurationMinutes, TimeUnit.MINUTES);
        }

        log.warn("Failed login attempt #{} for user: {}", count, username);
    }

    /**
     * Check if the account is currently locked out.
     */
    public boolean isLocked(String username) {
        String key = FAIL_PREFIX + username;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        return Integer.parseInt(value) >= maxAttempts;
    }

    /**
     * Get remaining seconds until the lockout expires. Returns 0 if not locked.
     */
    public long getRemainingLockoutSeconds(String username) {
        String key = FAIL_PREFIX + username;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return (ttl != null && ttl > 0) ? ttl : 0;
    }

    /**
     * Get remaining login attempts before lockout.
     */
    public int getRemainingAttempts(String username) {
        String key = FAIL_PREFIX + username;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return maxAttempts;
        }
        int used = Integer.parseInt(value);
        return Math.max(0, maxAttempts - used);
    }

    /**
     * Clear the fail counter after a successful login.
     */
    public void resetAttempts(String username) {
        redisTemplate.delete(FAIL_PREFIX + username);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
