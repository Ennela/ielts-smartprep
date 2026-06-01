package com.smartprep.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // 64-char secret for HMAC-SHA256
    private static final String SECRET = "this-is-a-test-secret-key-that-is-at-least-32-characters-long!!";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("should generate a non-null token")
    void generateToken_returnsNonNull() {
        String token = jwtTokenProvider.generateToken(1L, "testuser", "STUDENT");
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("should extract correct userId from token")
    void getUserIdFromToken_returnsCorrectId() {
        String token = jwtTokenProvider.generateToken(42L, "admin", "ADMIN");
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(42L, userId);
    }

    @Test
    @DisplayName("should extract correct role from token")
    void getRoleFromToken_returnsCorrectRole() {
        String token = jwtTokenProvider.generateToken(1L, "user", "ADMIN");
        String role = jwtTokenProvider.getRoleFromToken(token);
        assertEquals("ADMIN", role);
    }

    @Test
    @DisplayName("should validate a valid token")
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken(1L, "user", "STUDENT");
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
        String token = jwtTokenProvider.generateToken(1L, "user", "STUDENT");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtTokenProvider.validateToken(tampered));
    }

    @Test
    @DisplayName("should reject expired token")
    void validateToken_expiredToken_returnsFalse() {
        // Create a provider with 0ms expiration → token expires immediately
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0);
        String token = expiredProvider.generateToken(1L, "user", "STUDENT");

        // Sleep briefly to ensure expiration
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        assertFalse(expiredProvider.validateToken(token));
    }
}
