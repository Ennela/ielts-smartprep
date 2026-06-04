package com.smartprep.service;

import com.smartprep.exception.InvalidTokenException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages email verification flow:
 * 1. Generate a one-time verification token → store in Redis → send email
 * 2. Validate token → mark user as verified → delete token
 * <p>
 * Redis key: {@code email-verify:{token}} → userId (TTL = 24 hours)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final String VERIFY_PREFIX = "email-verify:";
    private static final long VERIFY_TOKEN_TTL_HOURS = 24;

    /**
     * Create a verification token and send the verification email.
     */
    public void createVerificationToken(Long userId, String email) {
        String token = UUID.randomUUID().toString();
        String key = VERIFY_PREFIX + token;
        redisTemplate.opsForValue().set(key, userId.toString(), VERIFY_TOKEN_TTL_HOURS, TimeUnit.HOURS);
        emailService.sendVerificationEmail(email, token);
        log.info("Verification email sent to: {} for userId: {}", email, userId);
    }

    /**
     * Verify a user's email address using the token.
     *
     * @throws InvalidTokenException if token is invalid or expired
     */
    public void verifyEmail(String token) {
        String key = VERIFY_PREFIX + token;
        String userIdStr = redisTemplate.opsForValue().get(key);

        if (userIdStr == null) {
            throw new InvalidTokenException("Email verification token is invalid or has expired");
        }

        Long userId = Long.parseLong(userIdStr);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("User not found for this verification token"));

        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete token after use (one-time)
        redisTemplate.delete(key);
        log.info("Email verified for userId: {}", userId);
    }

    /**
     * Resend verification email for a user.
     */
    public void resendVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email is already verified");
        }

        createVerificationToken(userId, user.getEmail());
    }
}
