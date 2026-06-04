package com.smartprep.service;

import com.smartprep.exception.InvalidTokenException;
import com.smartprep.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        tokenService = new TokenService(redisTemplate, jwtTokenProvider);
    }

    @Nested
    @DisplayName("storeRefreshToken")
    class StoreTests {

        @Test
        @DisplayName("should store JTI in Redis with correct TTL")
        void storeRefreshToken_success() {
            when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);

            tokenService.storeRefreshToken("jti-123", 42L);

            verify(valueOps).set(
                    eq("refresh:jti:jti-123"),
                    eq("42"),
                    eq(604800000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }
    }

    @Nested
    @DisplayName("isRefreshTokenValid")
    class ValidityTests {

        @Test
        @DisplayName("should return true when JTI exists and is not blacklisted")
        void valid_notBlacklisted() {
            when(redisTemplate.hasKey("blacklist:jti:jti-123")).thenReturn(false);
            when(redisTemplate.hasKey("refresh:jti:jti-123")).thenReturn(true);

            assertTrue(tokenService.isRefreshTokenValid("jti-123"));
        }

        @Test
        @DisplayName("should return false when JTI is blacklisted")
        void invalid_blacklisted() {
            when(redisTemplate.hasKey("blacklist:jti:jti-123")).thenReturn(true);

            assertFalse(tokenService.isRefreshTokenValid("jti-123"));
        }

        @Test
        @DisplayName("should return false when JTI does not exist")
        void invalid_notFound() {
            when(redisTemplate.hasKey("blacklist:jti:jti-123")).thenReturn(false);
            when(redisTemplate.hasKey("refresh:jti:jti-123")).thenReturn(false);

            assertFalse(tokenService.isRefreshTokenValid("jti-123"));
        }
    }

    @Nested
    @DisplayName("revokeRefreshToken")
    class RevokeTests {

        @Test
        @DisplayName("should blacklist JTI and delete from active set")
        void revoke_success() {
            when(redisTemplate.getExpire("refresh:jti:jti-456", TimeUnit.MILLISECONDS)).thenReturn(300000L);

            tokenService.revokeRefreshToken("jti-456");

            verify(valueOps).set(
                    eq("blacklist:jti:jti-456"),
                    eq("revoked"),
                    eq(300000L),
                    eq(TimeUnit.MILLISECONDS)
            );
            verify(redisTemplate).delete("refresh:jti:jti-456");
        }

        @Test
        @DisplayName("should use default TTL when key already expired")
        void revoke_expiredKey_usesDefault() {
            when(redisTemplate.getExpire("refresh:jti:jti-789", TimeUnit.MILLISECONDS)).thenReturn(-2L);
            when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);

            tokenService.revokeRefreshToken("jti-789");

            verify(valueOps).set(
                    eq("blacklist:jti:jti-789"),
                    eq("revoked"),
                    eq(604800000L),
                    eq(TimeUnit.MILLISECONDS)
            );
        }
    }

    @Nested
    @DisplayName("validateRefreshToken")
    class FullValidationTests {

        @Test
        @DisplayName("should return userId for a valid refresh token")
        void validate_success() {
            String token = "valid.refresh.token";
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTokenTypeFromToken(token)).thenReturn("refresh");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-ok");
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(42L);
            when(redisTemplate.hasKey("blacklist:jti:jti-ok")).thenReturn(false);
            when(redisTemplate.hasKey("refresh:jti:jti-ok")).thenReturn(true);

            Long userId = tokenService.validateRefreshToken(token);
            assertEquals(42L, userId);
        }

        @Test
        @DisplayName("should throw when token signature is invalid")
        void validate_invalidSignature() {
            when(jwtTokenProvider.validateToken("bad.token")).thenReturn(false);

            assertThrows(InvalidTokenException.class,
                    () -> tokenService.validateRefreshToken("bad.token"));
        }

        @Test
        @DisplayName("should throw when token is not a refresh token")
        void validate_wrongType() {
            when(jwtTokenProvider.validateToken("access.token")).thenReturn(true);
            when(jwtTokenProvider.getTokenTypeFromToken("access.token")).thenReturn("access");

            assertThrows(InvalidTokenException.class,
                    () -> tokenService.validateRefreshToken("access.token"));
        }

        @Test
        @DisplayName("should throw when JTI has been revoked")
        void validate_revokedJti() {
            String token = "revoked.refresh.token";
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTokenTypeFromToken(token)).thenReturn("refresh");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-revoked");
            when(redisTemplate.hasKey("blacklist:jti:jti-revoked")).thenReturn(true);

            assertThrows(InvalidTokenException.class,
                    () -> tokenService.validateRefreshToken(token));
        }
    }
}
