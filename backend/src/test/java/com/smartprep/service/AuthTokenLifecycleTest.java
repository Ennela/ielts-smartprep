package com.smartprep.service;

import com.smartprep.dto.request.ChangePasswordRequest;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.InvalidTokenException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the complete auth token lifecycle, focusing on security-critical flows:
 * refresh rotation, revocation, logout blacklisting, and password-change invalidation.
 */
@ExtendWith(MockitoExtension.class)
class AuthTokenLifecycleTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StatsService statsService;
    @Mock private TokenService tokenService;
    @Mock private LoginLockoutService loginLockoutService;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .username("testuser")
                .email("test@mail.com")
                .passwordHash("$2a$encoded")
                .displayName("Test User")
                .role(Role.STUDENT)
                .emailVerified(true)
                .targetReadingScore(new BigDecimal("6.5"))
                .targetWritingScore(new BigDecimal("6.5"))
                .targetListeningScore(new BigDecimal("6.5"))
                .build();
    }

    // ── Refresh Token Rotation ─────────────────────────────────────────

    @Nested
    @DisplayName("Refresh Token Rotation")
    class RefreshRotationTests {

        @Test
        @DisplayName("should revoke old refresh token and issue new pair on refresh")
        void refresh_rotatesTokens() {
            String oldRefresh = "old.refresh.token";

            when(tokenService.validateRefreshToken(oldRefresh)).thenReturn(1L);
            when(jwtTokenProvider.getJtiFromToken(oldRefresh)).thenReturn("old-jti");
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(jwtTokenProvider.generateAccessToken(1L, "testuser", "STUDENT")).thenReturn("new-access");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh");
            when(jwtTokenProvider.getJtiFromToken("new-refresh")).thenReturn("new-jti");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = userService.refreshToken(oldRefresh);

            // Old token must be revoked
            verify(tokenService).revokeRefreshToken("old-jti");
            // New token must be stored
            verify(tokenService).storeRefreshToken("new-jti", 1L);
            // New pair returned
            assertEquals("new-access", response.getToken());
            assertEquals("new-refresh", response.getRefreshToken());
        }

        @Test
        @DisplayName("should reject a revoked refresh token (replay attack)")
        void refresh_revokedToken_rejected() {
            String revokedToken = "revoked.refresh.token";

            when(tokenService.validateRefreshToken(revokedToken))
                    .thenThrow(new InvalidTokenException("Refresh token has been revoked"));

            assertThrows(InvalidTokenException.class,
                    () -> userService.refreshToken(revokedToken));

            // No new tokens should be issued
            verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString(), anyString());
            verify(jwtTokenProvider, never()).generateRefreshToken(anyLong());
        }

        @Test
        @DisplayName("should reject an expired refresh token")
        void refresh_expiredToken_rejected() {
            String expiredToken = "expired.refresh.token";

            when(tokenService.validateRefreshToken(expiredToken))
                    .thenThrow(new InvalidTokenException("Refresh token is invalid or expired"));

            InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                    () -> userService.refreshToken(expiredToken));
            assertTrue(ex.getMessage().contains("invalid or expired"));
        }

        @Test
        @DisplayName("should reject an access token used as refresh token")
        void refresh_accessTokenAsRefresh_rejected() {
            String accessToken = "access.token.used.as.refresh";

            when(tokenService.validateRefreshToken(accessToken))
                    .thenThrow(new InvalidTokenException("Token is not a refresh token"));

            InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                    () -> userService.refreshToken(accessToken));
            assertTrue(ex.getMessage().contains("not a refresh token"));
        }

        @Test
        @DisplayName("should throw when user no longer exists during refresh")
        void refresh_userDeleted_throws() {
            String refreshToken = "valid.refresh.token";

            when(tokenService.validateRefreshToken(refreshToken)).thenReturn(999L);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("jti-999");
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> userService.refreshToken(refreshToken));
        }
    }

    // ── Logout ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Logout")
    class LogoutTests {

        @Test
        @DisplayName("should revoke refresh token on logout")
        void logout_revokesRefreshToken() {
            String refreshToken = "refresh.to.revoke";

            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("jti-revoke");

            userService.logout(refreshToken, null);

            verify(tokenService).revokeRefreshToken("jti-revoke");
        }

        @Test
        @DisplayName("should blacklist access token on logout")
        void logout_blacklistsAccessToken() {
            String refreshToken = "refresh.token";
            String accessToken = "access.token";

            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("refresh-jti");
            when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(accessToken)).thenReturn("access-jti");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            userService.logout(refreshToken, accessToken);

            verify(tokenService).revokeRefreshToken("refresh-jti");
            verify(tokenService).blacklistAccessToken("access-jti", 900000L);
        }

        @Test
        @DisplayName("should not throw when refresh token is already invalid/expired")
        void logout_invalidRefreshToken_noop() {
            when(jwtTokenProvider.validateToken("expired")).thenReturn(false);

            assertDoesNotThrow(() -> userService.logout("expired", null));
            verify(tokenService, never()).revokeRefreshToken(any());
        }

        @Test
        @DisplayName("should handle null refresh token gracefully")
        void logout_nullRefreshToken_noop() {
            assertDoesNotThrow(() -> userService.logout(null, null));
            verify(tokenService, never()).revokeRefreshToken(any());
        }

        @Test
        @DisplayName("should handle null access token gracefully")
        void logout_nullAccessToken_noBlacklist() {
            when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken("refresh")).thenReturn("jti");

            userService.logout("refresh", null);

            verify(tokenService).revokeRefreshToken("jti");
            verify(tokenService, never()).blacklistAccessToken(any(), anyLong());
        }
    }

    // ── Password Change Token Invalidation ────────────────────────────

    @Nested
    @DisplayName("Password Change — Token Invalidation")
    class PasswordChangeTests {

        @Test
        @DisplayName("should blacklist access token after password change")
        void changePassword_blacklistsAccessToken() {
            String accessToken = "current.access.token";

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("oldpass", "$2a$encoded")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");
            when(jwtTokenProvider.validateToken(accessToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(accessToken)).thenReturn("access-jti");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("oldpass");
            req.setNewPassword("newpass");

            userService.changePassword(1L, req, null, accessToken);

            verify(userRepository).save(testUser);
            verify(tokenService).blacklistAccessToken("access-jti", 900000L);
        }

        @Test
        @DisplayName("should revoke refresh token after password change when provided")
        void changePassword_revokesRefreshToken() {
            String refreshToken = "current.refresh.token";

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("oldpass", "$2a$encoded")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("refresh-jti");

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("oldpass");
            req.setNewPassword("newpass");

            userService.changePassword(1L, req, refreshToken, null);

            verify(tokenService).revokeRefreshToken("refresh-jti");
        }

        @Test
        @DisplayName("should still change password when no tokens provided for invalidation")
        void changePassword_noTokens_stillChanges() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("oldpass", "$2a$encoded")).thenReturn(true);
            when(passwordEncoder.encode("newpass")).thenReturn("$2a$newencoded");

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("oldpass");
            req.setNewPassword("newpass");

            userService.changePassword(1L, req, null, null);

            verify(userRepository).save(testUser);
            verify(tokenService, never()).revokeRefreshToken(any());
            verify(tokenService, never()).blacklistAccessToken(any(), anyLong());
        }

        @Test
        @DisplayName("should throw on incorrect current password without invalidating tokens")
        void changePassword_wrongPassword_noTokenInvalidation() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches("wrongpass", "$2a$encoded")).thenReturn(false);

            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("wrongpass");
            req.setNewPassword("newpass");

            assertThrows(IllegalArgumentException.class,
                    () -> userService.changePassword(1L, req, "refresh", "access"));

            verify(userRepository, never()).save(any());
            verify(tokenService, never()).revokeRefreshToken(any());
            verify(tokenService, never()).blacklistAccessToken(any(), anyLong());
        }
    }
}
