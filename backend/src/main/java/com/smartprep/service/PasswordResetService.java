package com.smartprep.service;

import com.smartprep.exception.InvalidTokenException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages password reset flow:
 * 1. Generate a one-time reset token → store in Redis → send email
 * 2. Validate token → update password → delete token
 * <p>
 * Redis key: {@code pwd-reset:{token}} → email (TTL = 15 minutes)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final String RESET_PREFIX = "pwd-reset:";
    private static final long RESET_TOKEN_TTL_MINUTES = 15;

    /**
     * Create a password reset token and send the reset email.
     * <p>
     * Always returns successfully even if the email doesn't exist (prevents user enumeration).
     */
    public void createResetToken(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            String key = RESET_PREFIX + token;
            redisTemplate.opsForValue().set(key, email, RESET_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
            emailService.sendPasswordResetEmail(email, token);
            log.info("Password reset token created for email: {}", email);
        });
        // If user doesn't exist, silently succeed (anti-enumeration)
    }

    /**
     * Reset the password using a valid token.
     *
     * @throws InvalidTokenException if token is invalid or expired
     */
    public void resetPassword(String token, String newPassword) {
        String key = RESET_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);

        if (email == null) {
            throw new InvalidTokenException("Password reset token is invalid or has expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidTokenException("User not found for this reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Delete token after use (one-time)
        redisTemplate.delete(key);
        log.info("Password reset completed for email: {}", email);
    }
}
