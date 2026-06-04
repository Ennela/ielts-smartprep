package com.smartprep.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginLockoutServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private LoginLockoutService lockoutService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lockoutService = new LoginLockoutService(redisTemplate);
        ReflectionTestUtils.setField(lockoutService, "maxAttempts", 5);
        ReflectionTestUtils.setField(lockoutService, "lockoutDurationMinutes", 15);
    }

    @Nested
    @DisplayName("recordFailedAttempt")
    class RecordTests {

        @Test
        @DisplayName("should increment counter and set TTL on first attempt")
        void recordFirst_setsTTL() {
            when(valueOps.increment("login-fail:testuser")).thenReturn(1L);

            lockoutService.recordFailedAttempt("testuser");

            verify(valueOps).increment("login-fail:testuser");
            verify(redisTemplate).expire("login-fail:testuser", 15, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("should increment counter without resetting TTL on subsequent attempts")
        void recordSubsequent_noTTLReset() {
            when(valueOps.increment("login-fail:testuser")).thenReturn(3L);

            lockoutService.recordFailedAttempt("testuser");

            verify(valueOps).increment("login-fail:testuser");
            verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Nested
    @DisplayName("isLocked")
    class LockCheckTests {

        @Test
        @DisplayName("should return false when no failures recorded")
        void notLocked_noKey() {
            when(valueOps.get("login-fail:testuser")).thenReturn(null);

            assertFalse(lockoutService.isLocked("testuser"));
        }

        @Test
        @DisplayName("should return false when failures < max attempts")
        void notLocked_belowThreshold() {
            when(valueOps.get("login-fail:testuser")).thenReturn("4");

            assertFalse(lockoutService.isLocked("testuser"));
        }

        @Test
        @DisplayName("should return true when failures == max attempts")
        void locked_atThreshold() {
            when(valueOps.get("login-fail:testuser")).thenReturn("5");

            assertTrue(lockoutService.isLocked("testuser"));
        }

        @Test
        @DisplayName("should return true when failures > max attempts")
        void locked_aboveThreshold() {
            when(valueOps.get("login-fail:testuser")).thenReturn("7");

            assertTrue(lockoutService.isLocked("testuser"));
        }
    }

    @Nested
    @DisplayName("getRemainingAttempts")
    class RemainingTests {

        @Test
        @DisplayName("should return max attempts when no failures")
        void full_remaining() {
            when(valueOps.get("login-fail:testuser")).thenReturn(null);

            assertEquals(5, lockoutService.getRemainingAttempts("testuser"));
        }

        @Test
        @DisplayName("should return correct remaining after some failures")
        void partial_remaining() {
            when(valueOps.get("login-fail:testuser")).thenReturn("3");

            assertEquals(2, lockoutService.getRemainingAttempts("testuser"));
        }

        @Test
        @DisplayName("should return 0 when all attempts used")
        void zero_remaining() {
            when(valueOps.get("login-fail:testuser")).thenReturn("5");

            assertEquals(0, lockoutService.getRemainingAttempts("testuser"));
        }
    }

    @Test
    @DisplayName("resetAttempts should delete the fail counter key")
    void resetAttempts_deletesKey() {
        lockoutService.resetAttempts("testuser");

        verify(redisTemplate).delete("login-fail:testuser");
    }
}
