package com.smartprep.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.LoginRequest;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test proving that the auth rate limit interceptor returns
 * HTTP 429 with the standard {@code ApiResponse} error body when the
 * request limit is exceeded on {@code /api/v1/auth/login}.
 * <p>
 * Uses a mocked {@link ProxyManager} so no real Redis is needed,
 * while the full MVC pipeline (interceptor → controller → exception handler) is exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
class AuthRateLimitIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        // Set low rate limit so we can exhaust it easily
        registry.add("app.rate-limit.auth-login-capacity", () -> "3");
        registry.add("app.rate-limit.auth-login-refill-minutes", () -> "1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProxyManager<String> proxyManager;

    @Test
    @DisplayName("should return HTTP 429 with RATE_LIMIT_EXCEEDED error when login rate limit is exceeded")
    void login_rateLimitExceeded_returns429() throws Exception {
        // Simulate a bucket that allows the first 3 requests and blocks the 4th
        AtomicInteger remaining = new AtomicInteger(3);

        BucketProxy mockBucket = org.mockito.Mockito.mock(BucketProxy.class);
        when(mockBucket.tryConsume(1)).thenAnswer(inv -> remaining.getAndDecrement() > 0);

        @SuppressWarnings("unchecked")
        RemoteBucketBuilder<String> mockBuilder = org.mockito.Mockito.mock(RemoteBucketBuilder.class);
        lenient().when(mockBuilder.build(any(String.class), any(Supplier.class))).thenReturn(mockBucket);
        when(proxyManager.builder()).thenReturn(mockBuilder);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("nonexistent_user");
        loginRequest.setPassword("wrong_password");
        String body = objectMapper.writeValueAsString(loginRequest);

        // First 3 requests should pass through rate limiter
        // (they'll fail with 400 "Invalid username" from the controller — that's expected)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        // 4th request should be blocked by rate limiter before reaching the controller
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.errorCode", is("RATE_LIMIT_EXCEEDED")))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("should allow login requests when under rate limit")
    void login_underLimit_passes() throws Exception {
        BucketProxy mockBucket = org.mockito.Mockito.mock(BucketProxy.class);
        when(mockBucket.tryConsume(1)).thenReturn(true);

        @SuppressWarnings("unchecked")
        RemoteBucketBuilder<String> mockBuilder = org.mockito.Mockito.mock(RemoteBucketBuilder.class);
        lenient().when(mockBuilder.build(any(String.class), any(Supplier.class))).thenReturn(mockBucket);
        when(proxyManager.builder()).thenReturn(mockBuilder);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("nonexistent_user");
        loginRequest.setPassword("wrong_password");
        String body = objectMapper.writeValueAsString(loginRequest);

        // Request passes rate limiter, reaches controller (gets 400 for bad credentials)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
