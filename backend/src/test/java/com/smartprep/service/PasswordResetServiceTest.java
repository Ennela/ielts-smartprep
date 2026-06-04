package com.smartprep.service;

import com.smartprep.exception.InvalidTokenException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        passwordResetService = new PasswordResetService(
                redisTemplate, userRepository, passwordEncoder, emailService);
    }

    @Nested
    @DisplayName("createResetToken")
    class CreateTokenTests {

        @Test
        @DisplayName("should store token in Redis and send email when user exists")
        void createResetToken_userExists() {
            User user = User.builder().userId(1L).email("test@mail.com").build();
            when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));

            passwordResetService.createResetToken("test@mail.com");

            verify(valueOps).set(startsWith("pwd-reset:"), eq("test@mail.com"),
                    eq(15L), eq(TimeUnit.MINUTES));
            verify(emailService).sendPasswordResetEmail(eq("test@mail.com"), anyString());
        }

        @Test
        @DisplayName("should silently succeed when email does not exist (anti-enumeration)")
        void createResetToken_userNotExists_noException() {
            when(userRepository.findByEmail("ghost@mail.com")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> passwordResetService.createResetToken("ghost@mail.com"));

            verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("resetPassword")
    class ResetPasswordTests {

        @Test
        @DisplayName("should update password and delete token when valid")
        void resetPassword_success() {
            String token = "valid-token-uuid";
            User user = User.builder().userId(1L).email("test@mail.com").passwordHash("old").build();

            when(valueOps.get("pwd-reset:" + token)).thenReturn("test@mail.com");
            when(userRepository.findByEmail("test@mail.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("NewPass123")).thenReturn("$2a$encoded-new");

            passwordResetService.resetPassword(token, "NewPass123");

            assertEquals("$2a$encoded-new", user.getPasswordHash());
            verify(userRepository).save(user);
            verify(redisTemplate).delete("pwd-reset:" + token);
        }

        @Test
        @DisplayName("should throw InvalidTokenException for expired/invalid token")
        void resetPassword_invalidToken() {
            when(valueOps.get("pwd-reset:expired-token")).thenReturn(null);

            InvalidTokenException ex = assertThrows(InvalidTokenException.class,
                    () -> passwordResetService.resetPassword("expired-token", "NewPass123"));

            assertTrue(ex.getMessage().contains("invalid or has expired"));
        }

        @Test
        @DisplayName("should throw when user no longer exists for token's email")
        void resetPassword_userDeleted() {
            when(valueOps.get("pwd-reset:valid-token")).thenReturn("deleted@mail.com");
            when(userRepository.findByEmail("deleted@mail.com")).thenReturn(Optional.empty());

            assertThrows(InvalidTokenException.class,
                    () -> passwordResetService.resetPassword("valid-token", "NewPass123"));
        }
    }
}
