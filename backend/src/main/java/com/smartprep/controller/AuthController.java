package com.smartprep.controller;

import com.smartprep.dto.request.*;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.EmailVerificationService;
import com.smartprep.service.PasswordResetService;
import com.smartprep.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, token management, and password recovery API")
public class AuthController {

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    // ── Registration & Login ──────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new account and sends a verification email")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Registration successful. Please check your email to verify your account."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login to user account", description = "Authenticates credentials and returns access + refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    // ── Token Management ──────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a new access + refresh token pair (rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = userService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(response, "Token refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token", description = "Revokes the refresh token so it can no longer be used")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        userService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
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
    @Operation(summary = "Change user password", description = "Updates the account password after verifying current password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed successfully"));
    }
}
