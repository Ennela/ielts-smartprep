package com.smartprep.controller;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RefreshTokenRequest;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.InvalidTokenException;
import com.smartprep.security.JwtTokenProvider;
import com.smartprep.service.EmailVerificationService;
import com.smartprep.service.PasswordResetService;
import com.smartprep.service.StorageService;
import com.smartprep.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenControllerTest {

    @Mock private UserService userService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private StorageService storageService;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                userService,
                passwordResetService,
                emailVerificationService,
                storageService,
                jwtTokenProvider);
        lenient().when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
    }

    @Test
    @DisplayName("login should set HttpOnly refresh cookie and omit refresh token from body")
    void login_setsRefreshCookieAndOmitsBodyRefreshToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        when(userService.login(request)).thenReturn(authResponse("access-token", "refresh-token"));

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertRefreshCookie(response, "refresh-token");
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().getData().getToken());
        assertNull(response.getBody().getData().getRefreshToken());
    }

    @Test
    @DisplayName("refresh should prefer cookie token over request body fallback")
    void refresh_prefersCookieToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("body-refresh");
        when(userService.refreshToken("cookie-refresh"))
                .thenReturn(authResponse("new-access", "new-refresh"));

        ResponseEntity<ApiResponse<AuthResponse>> response =
                authController.refreshToken("cookie-refresh", request);

        verify(userService).refreshToken("cookie-refresh");
        assertRefreshCookie(response, "new-refresh");
        assertNotNull(response.getBody());
        assertEquals("new-access", response.getBody().getData().getToken());
        assertNull(response.getBody().getData().getRefreshToken());
    }

    @Test
    @DisplayName("refresh should accept request body fallback for legacy clients")
    void refresh_usesRequestBodyFallback() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("legacy-refresh");
        when(userService.refreshToken("legacy-refresh"))
                .thenReturn(authResponse("new-access", "new-refresh"));

        authController.refreshToken(null, request);

        verify(userService).refreshToken("legacy-refresh");
    }

    @Test
    @DisplayName("refresh should reject missing refresh token")
    void refresh_missingToken_rejected() {
        assertThrows(InvalidTokenException.class,
                () -> authController.refreshToken(null, null));
        verify(userService, never()).refreshToken(any());
    }

    @Test
    @DisplayName("refresh should propagate expired/revoked token rejection")
    void refresh_expiredToken_rejected() {
        when(userService.refreshToken("expired-refresh"))
                .thenThrow(new InvalidTokenException("Refresh token is invalid or expired"));

        InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                () -> authController.refreshToken("expired-refresh", null));

        assertTrue(ex.getMessage().contains("invalid or expired"));
    }

    @Test
    @DisplayName("logout should revoke cookie refresh token and clear cookie")
    void logout_revokesRefreshAndClearsCookie() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("body-refresh");

        ResponseEntity<ApiResponse<Void>> response =
                authController.logout("cookie-refresh", request, "Bearer access-token");

        verify(userService).logout("cookie-refresh", "access-token");
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refreshToken=;"));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=0"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    private AuthResponse authResponse(String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .userId(1L)
                .username("testuser")
                .token(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(900000L)
                .role("STUDENT")
                .build();
    }

    private void assertRefreshCookie(ResponseEntity<?> response, String refreshToken) {
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith("refreshToken=" + refreshToken));
        assertTrue(setCookie.contains("Path=/api/v1/auth"));
        assertTrue(setCookie.contains("Max-Age=604800"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }
}
