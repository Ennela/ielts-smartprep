package com.smartprep.service;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RegisterRequest;
import com.smartprep.dto.request.UpdateProfileRequest;
import com.smartprep.dto.request.ChangePasswordRequest;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.AccountLockedException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StatsService statsService;
    private final TokenService tokenService;
    private final LoginLockoutService loginLockoutService;
    private final EmailVerificationService emailVerificationService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getUsername())
                .targetReadingScore(new BigDecimal("6.5"))
                .targetWritingScore(new BigDecimal("6.5"))
                .targetListeningScore(new BigDecimal("6.5"))
                .build();

        user = userRepository.save(user);

        // Generate access + refresh token pair
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        // Store refresh token JTI in Redis
        String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
        tokenService.storeRefreshToken(refreshJti, user.getUserId());

        // Send verification email (async)
        emailVerificationService.createVerificationToken(user.getUserId(), user.getEmail());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        // Check lockout before attempting authentication
        if (loginLockoutService.isLocked(request.getUsername())) {
            long remainingSec = loginLockoutService.getRemainingLockoutSeconds(request.getUsername());
            throw new AccountLockedException(remainingSec, loginLockoutService.getMaxAttempts());
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginLockoutService.recordFailedAttempt(request.getUsername());
            int remaining = loginLockoutService.getRemainingAttempts(request.getUsername());
            throw new IllegalArgumentException(
                    "Invalid username or password. " + remaining + " attempt(s) remaining.");
        }

        // Successful login — clear fail counter
        loginLockoutService.resetAttempts(request.getUsername());

        // Generate access + refresh token pair
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        // Store refresh token JTI in Redis
        String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
        tokenService.storeRefreshToken(refreshJti, user.getUserId());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Refresh the access token using a valid refresh token.
     * Implements refresh token rotation: old refresh token is revoked, new pair is issued.
     */
    public AuthResponse refreshToken(String refreshToken) {
        Long userId = tokenService.validateRefreshToken(refreshToken);

        // Revoke old refresh token (rotation)
        String oldJti = jwtTokenProvider.getJtiFromToken(refreshToken);
        tokenService.revokeRefreshToken(oldJti);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate new pair
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getUserId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId());

        String newJti = jwtTokenProvider.getJtiFromToken(newRefreshToken);
        tokenService.storeRefreshToken(newJti, user.getUserId());

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    /**
     * Logout by revoking the refresh token.
     */
    public void logout(String refreshToken) {
        if (jwtTokenProvider.validateToken(refreshToken)) {
            String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
            tokenService.revokeRefreshToken(jti);
        }
    }

    public AuthResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return buildAuthResponse(user, null, null);
    }

    public AuthResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setDisplayName(request.getDisplayName());
        user.setTargetReadingScore(request.getTargetReadingScore());
        user.setTargetWritingScore(request.getTargetWritingScore());
        user.setTargetListeningScore(request.getTargetListeningScore());
        
        user = userRepository.save(user);
        statsService.evictOverviewCache(userId);
        return buildAuthResponse(user, null, null);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect current password");
        }
        
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private AuthResponse buildAuthResponse(User user, String token, String refreshToken) {
        return AuthResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .token(token)
                .refreshToken(refreshToken)
                .expiresIn(token != null ? jwtTokenProvider.getAccessExpirationMs() : null)
                .role(user.getRole() != null ? user.getRole().name() : "STUDENT")
                .emailVerified(user.getEmailVerified())
                .targetReadingScore(user.getTargetReadingScore())
                .targetWritingScore(user.getTargetWritingScore())
                .targetListeningScore(user.getTargetListeningScore())
                .build();
    }
}
