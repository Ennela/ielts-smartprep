package com.smartprep.controller;

import com.smartprep.dto.request.*;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.InvalidTokenException;
import com.smartprep.model.entity.User;
import com.smartprep.security.JwtTokenProvider;
import com.smartprep.service.EmailVerificationService;
import com.smartprep.service.PasswordResetService;
import com.smartprep.service.UserService;
import com.smartprep.service.StorageService;
import org.springframework.web.multipart.MultipartFile;
import java.time.Duration;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, token management, and password recovery API")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String AUTH_COOKIE_PATH = "/api/v1/auth";

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;
    private final StorageService storageService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.security.secure-cookies:false}")
    private boolean secureCookies;

    // ── Registration & Login ──────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new account and sends a verification email")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return authResponseWithRefreshCookie(response,
                "Registration successful. Please check your email to verify your account.",
                HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login to user account", description = "Authenticates credentials and returns access + refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return authResponseWithRefreshCookie(response, "Login successful", HttpStatus.OK);
    }

    // ── Token Management ──────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a new access + refresh token pair (rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = resolveRefreshToken(refreshTokenCookie, request);
        AuthResponse response = userService.refreshToken(refreshToken);
        return authResponseWithRefreshCookie(response, "Token refreshed", HttpStatus.OK);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token", description = "Revokes the refresh token and blacklists the current access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String accessToken = extractBearerToken(authHeader);
        userService.logout(resolveRefreshTokenOrNull(refreshTokenCookie, request), accessToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    // ── Password Recovery ─────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Sends a password reset email if the address is registered. Always returns 200 to prevent user enumeration.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.createResetToken(request.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(null,
                "If an account with that email exists, a password reset link has been sent."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with token", description = "Resets the account password using the token received via email")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Password has been reset successfully. Please login with your new password."));
    }

    // ── Email Verification ────────────────────────────────────────────────

    @GetMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verifies the user's email using the token received via email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(null, "Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Resends the verification email to the authenticated user")
    public ResponseEntity<ApiResponse<Void>> resendVerification(@AuthenticationPrincipal User user) {
        emailVerificationService.resendVerification(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Verification email sent"));
    }

    // ── Profile ───────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<AuthResponse>> getProfile(@AuthenticationPrincipal User user) {
        AuthResponse response = userService.getProfile(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile information", description = "Updates displayName and target scores")
    public ResponseEntity<ApiResponse<AuthResponse>> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        AuthResponse response = userService.updateProfile(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Profile updated successfully"));
    }

    @PutMapping("/password")
    @Operation(summary = "Change user password", description = "Updates the account password after verifying current password. Invalidates current tokens.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String accessToken = extractBearerToken(authHeader);
        // refreshToken is not directly available here; pass null.
        // The access token blacklisting will force re-auth, and the refresh token
        // will fail to issue new access tokens once the user re-authenticates.
        userService.changePassword(user.getUserId(), request, null, accessToken);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed successfully"));
    }

    @PostMapping(value = "/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload user avatar", description = "Uploads a new user avatar to MinIO storage")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/png") && !contentType.equals("image/jpeg") && !contentType.equals("image/jpg"))) {
            throw new IllegalArgumentException("Only PNG or JPG images are allowed");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must be under 5MB");
        }
        String originalName = file.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".jpg";
        String key = "avatar_" + user.getUserId() + "_" + System.currentTimeMillis() + extension;
        
        String avatarUrl = storageService.uploadImage(key, file.getBytes(), contentType);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("avatarUrl", avatarUrl), "Avatar uploaded successfully"));
    }

    @GetMapping(value = "/avatar/{fileName}")
    @Operation(summary = "Serve user avatar", description = "Serves the user's avatar image from storage")
    public ResponseEntity<org.springframework.core.io.Resource> getAvatarFile(@PathVariable String fileName) {
        try {
            byte[] imageBytes = storageService.downloadAudio(fileName);
            org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(imageBytes);
            String contentType = "image/jpeg";
            if (fileName.endsWith(".png")) {
                contentType = "image/png";
            }
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            try {
                org.springframework.core.io.ClassPathResource resource =
                    new org.springframework.core.io.ClassPathResource("static/assets/avatars/avatar_sarah.png");
                if (resource.exists()) {
                    return ResponseEntity.ok()
                            .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                            .body(resource);
                }
            } catch (Exception ex) {
                // Ignore
            }
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String extractBearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private ResponseEntity<ApiResponse<AuthResponse>> authResponseWithRefreshCookie(
            AuthResponse response,
            String message,
            HttpStatus status) {
        ResponseCookie cookie = buildRefreshCookie(response.getRefreshToken());
        response.setRefreshToken(null);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(response, message));
    }

    private ResponseCookie buildRefreshCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshExpirationMs()))
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
    }

    private String resolveRefreshToken(String refreshTokenCookie, RefreshTokenRequest request) {
        String refreshToken = resolveRefreshTokenOrNull(refreshTokenCookie, request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new InvalidTokenException("Refresh token is required");
        }
        return refreshToken;
    }

    private String resolveRefreshTokenOrNull(String refreshTokenCookie, RefreshTokenRequest request) {
        if (StringUtils.hasText(refreshTokenCookie)) {
            return refreshTokenCookie;
        }
        return request != null ? request.getRefreshToken() : null;
    }
}
