package com.smartprep.security;

import com.smartprep.exception.RateLimitExceededException;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitInterceptorTest {

    @Mock private ProxyManager<String> proxyManager;
    @Mock private RemoteBucketBuilder<String> bucketBuilder;
    @Mock private io.github.bucket4j.distributed.BucketProxy bucket;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    @InjectMocks private AuthRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(interceptor, "loginCapacity", 10);
        ReflectionTestUtils.setField(interceptor, "loginRefillMinutes", 1);
        ReflectionTestUtils.setField(interceptor, "registerCapacity", 5);
        ReflectionTestUtils.setField(interceptor, "registerRefillMinutes", 1);
    }

    private void stubBucket(boolean allowed) {
        when(proxyManager.builder()).thenReturn(bucketBuilder);
        when(bucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucket);
        when(bucket.tryConsume(1)).thenReturn(allowed);
    }

    // ── Login endpoint ────────────────────────────────────────────────────

    @Test
    @DisplayName("should allow login request when under rate limit")
    void login_allowed() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        stubBucket(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(bucket).tryConsume(1);
    }

    @Test
    @DisplayName("should block login request when rate limit exceeded")
    void login_blocked() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        stubBucket(false);

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () ->
                interceptor.preHandle(request, response, new Object())
        );

        assertTrue(ex.getMessage().contains("Too many requests"));
    }

    @Test
    @DisplayName("should use 'login' bucket key for /login endpoint")
    void login_usesCorrectKey() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        stubBucket(true);

        interceptor.preHandle(request, response, new Object());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bucketBuilder).build(keyCaptor.capture(), any(Supplier.class));
        assertEquals("auth-rate-limit:login:10.0.0.1", keyCaptor.getValue());
    }

    // ── Register endpoint ─────────────────────────────────────────────────

    @Test
    @DisplayName("should use 'register' bucket key for /register endpoint")
    void register_usesSeparatePolicy() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/register");
        when(request.getRemoteAddr()).thenReturn("172.16.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        stubBucket(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bucketBuilder).build(keyCaptor.capture(), any(Supplier.class));
        assertEquals("auth-rate-limit:register:172.16.0.5", keyCaptor.getValue());
    }

    // ── Forgot-password endpoint ──────────────────────────────────────────

    @Test
    @DisplayName("should use 'login' bucket key for /forgot-password endpoint")
    void forgotPassword_usesLoginPolicy() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/forgot-password");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        stubBucket(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(bucketBuilder).build(keyCaptor.capture(), any(Supplier.class));
        assertEquals("auth-rate-limit:login:10.0.0.2", keyCaptor.getValue());
    }

    // ── X-Forwarded-For IP resolution ─────────────────────────────────────

    @Test
    @DisplayName("should resolve client IP from X-Forwarded-For header (first IP)")
    void xff_resolvesFirstIp() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 70.41.3.18, 150.172.238.178");

        String ip = interceptor.resolveClientIp(request);

        assertEquals("203.0.113.50", ip);
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For is absent")
    void xff_absent_fallsBack() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String ip = interceptor.resolveClientIp(request);

        assertEquals("127.0.0.1", ip);
    }

    @Test
    @DisplayName("should fall back to remoteAddr when X-Forwarded-For is blank")
    void xff_blank_fallsBack() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        String ip = interceptor.resolveClientIp(request);

        assertEquals("127.0.0.1", ip);
    }
}
