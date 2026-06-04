package com.smartprep.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // 64-char secret for HMAC-SHA256
    private static final String SECRET = "this-is-a-test-secret-key-that-is-at-least-32-characters-long!!";
    private static final long ACCESS_EXPIRATION_MS = 900000; // 15 min
    private static final long REFRESH_EXPIRATION_MS = 604800000; // 7 days

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, ACCESS_EXPIRATION_MS, REFRESH_EXPIRATION_MS);
    }

    @Nested
    @DisplayName("Access Token")
    class AccessTokenTests {

        @Test
        @DisplayName("should generate a non-null access token")
        void generateAccessToken_returnsNonNull() {
            String token = jwtTokenProvider.generateAccessToken(1L, "testuser", "STUDENT");
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        @DisplayName("should have 'access' type")
        void accessToken_hasCorrectType() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            assertEquals("access", jwtTokenProvider.getTokenTypeFromToken(token));
        }

        @Test
        @DisplayName("should contain JTI claim")
        void accessToken_hasJti() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            String jti = jwtTokenProvider.getJtiFromToken(token);
            assertNotNull(jti);
            assertFalse(jti.isEmpty());
        }

        @Test
        @DisplayName("should generate unique JTIs for each token")
        void accessToken_uniqueJtis() {
            String token1 = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            String token2 = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            assertNotEquals(jwtTokenProvider.getJtiFromToken(token1),
                    jwtTokenProvider.getJtiFromToken(token2));
        }

        @Test
        @DisplayName("should extract correct userId from access token")
        void accessToken_correctUserId() {
            String token = jwtTokenProvider.generateAccessToken(42L, "admin", "ADMIN");
            assertEquals(42L, jwtTokenProvider.getUserIdFromToken(token));
        }

        @Test
        @DisplayName("should extract correct role from access token")
        void accessToken_correctRole() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user", "ADMIN");
            assertEquals("ADMIN", jwtTokenProvider.getRoleFromToken(token));
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("should generate a non-null refresh token")
        void generateRefreshToken_returnsNonNull() {
            String token = jwtTokenProvider.generateRefreshToken(1L);
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }

        @Test
        @DisplayName("should have 'refresh' type")
        void refreshToken_hasCorrectType() {
            String token = jwtTokenProvider.generateRefreshToken(1L);
            assertEquals("refresh", jwtTokenProvider.getTokenTypeFromToken(token));
        }

        @Test
        @DisplayName("should extract correct userId from refresh token")
        void refreshToken_correctUserId() {
            String token = jwtTokenProvider.generateRefreshToken(99L);
            assertEquals(99L, jwtTokenProvider.getUserIdFromToken(token));
        }

        @Test
        @DisplayName("should contain JTI claim")
        void refreshToken_hasJti() {
            String token = jwtTokenProvider.generateRefreshToken(1L);
            assertNotNull(jwtTokenProvider.getJtiFromToken(token));
        }
    }

    @Nested
    @DisplayName("Token Type Differentiation")
    class TypeTests {

        @Test
        @DisplayName("access and refresh tokens should have different types")
        void differentTypes() {
            String access = jwtTokenProvider.generateAccessToken(1L, "u", "STUDENT");
            String refresh = jwtTokenProvider.generateRefreshToken(1L);

            assertEquals("access", jwtTokenProvider.getTokenTypeFromToken(access));
            assertEquals("refresh", jwtTokenProvider.getTokenTypeFromToken(refresh));
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should validate a valid access token")
        void validateToken_validAccess_returnsTrue() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            assertTrue(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("should validate a valid refresh token")
        void validateToken_validRefresh_returnsTrue() {
            String token = jwtTokenProvider.generateRefreshToken(1L);
            assertTrue(jwtTokenProvider.validateToken(token));
        }

        @Test
        @DisplayName("should reject an invalid token")
        void validateToken_invalidToken_returnsFalse() {
            assertFalse(jwtTokenProvider.validateToken("totally.invalid.token"));
        }

        @Test
        @DisplayName("should reject a tampered token")
        void validateToken_tamperedToken_returnsFalse() {
            String token = jwtTokenProvider.generateAccessToken(1L, "user", "STUDENT");
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            assertFalse(jwtTokenProvider.validateToken(tampered));
        }

        @Test
        @DisplayName("should reject expired token")
        void validateToken_expiredToken_returnsFalse() {
            JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0, 0);
            String token = expiredProvider.generateAccessToken(1L, "user", "STUDENT");

            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            assertFalse(expiredProvider.validateToken(token));
        }
    }

    @Nested
    @DisplayName("Backward Compatibility")
    class BackwardCompatTests {

        @Test
        @DisplayName("deprecated generateToken should produce valid access token")
        @SuppressWarnings("deprecation")
        void legacyGenerateToken_producesAccessToken() {
            String token = jwtTokenProvider.generateToken(1L, "user", "STUDENT");
            assertNotNull(token);
            assertTrue(jwtTokenProvider.validateToken(token));
            assertEquals("access", jwtTokenProvider.getTokenTypeFromToken(token));
        }
    }
}
