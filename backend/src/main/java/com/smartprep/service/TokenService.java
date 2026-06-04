package com.smartprep.service;

import com.smartprep.exception.InvalidTokenException;
import com.smartprep.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Manages refresh token lifecycle in Redis.
 * <p>
 * Redis key patterns:
 * <ul>
 *   <li>{@code refresh:jti:{jti}} → userId — active refresh token (TTL = refresh expiry)</li>
 *   <li>{@code blacklist:jti:{jti}} → "revoked" — revoked token (TTL = remaining)</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    private static final String REFRESH_PREFIX = "refresh:jti:";
    private static final String BLACKLIST_PREFIX = "blacklist:jti:";

    /**
     * Store a refresh token's JTI in Redis for validation.
     */
    public void storeRefreshToken(String jti, Long userId) {
        String key = REFRESH_PREFIX + jti;
        long ttlMs = jwtTokenProvider.getRefreshExpirationMs();
        redisTemplate.opsForValue().set(key, userId.toString(), ttlMs, TimeUnit.MILLISECONDS);
        log.debug("Stored refresh JTI: {} for user: {}", jti, userId);
    }

    /**
     * Check if a refresh token JTI is valid (exists and not blacklisted).
     */
    public boolean isRefreshTokenValid(String jti) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_PREFIX + jti));
    }

    /**
     * Revoke a refresh token by moving its JTI to the blacklist.
     */
    public void revokeRefreshToken(String jti) {
        String refreshKey = REFRESH_PREFIX + jti;
        Long ttl = redisTemplate.getExpire(refreshKey, TimeUnit.MILLISECONDS);

        // Blacklist for the remaining TTL (or 7 days if key already expired)
        long blacklistTtl = (ttl != null && ttl > 0) ? ttl : jwtTokenProvider.getRefreshExpirationMs();
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "revoked", blacklistTtl, TimeUnit.MILLISECONDS);

        // Remove from active set
        redisTemplate.delete(refreshKey);
        log.info("Revoked refresh JTI: {}", jti);
    }

    /**
     * Validate a refresh token string fully: signature, type, JTI existence, blacklist check.
     *
     * @return userId from the token
     * @throws InvalidTokenException if invalid
     */
    public Long validateRefreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        String type = jwtTokenProvider.getTokenTypeFromToken(refreshToken);
        if (!"refresh".equals(type)) {
            throw new InvalidTokenException("Token is not a refresh token");
        }

        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        if (!isRefreshTokenValid(jti)) {
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        return jwtTokenProvider.getUserIdFromToken(refreshToken);
    }
}
