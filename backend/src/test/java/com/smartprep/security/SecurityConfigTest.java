package com.smartprep.security;

import com.smartprep.config.CorsConfig;
import com.smartprep.config.SecurityConfig;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Lightweight integration test for {@link SecurityConfig} and {@link CorsConfig}.
 * <p>
 * Uses a minimal Spring context with only security-related beans (no DB, no Redis,
 * no Testcontainers). Verifies:
 * <ul>
 *   <li>Admin endpoints require ADMIN role and reject unauthenticated requests</li>
 *   <li>Public endpoints are accessible without authentication</li>
 *   <li>Authenticated endpoints reject unauthenticated requests</li>
 *   <li>CORS headers are returned correctly for allowed origins</li>
 *   <li>CORS rejects disallowed origins</li>
 *   <li>CSP header is present in responses</li>
 * </ul>
 */
@SpringBootTest(
        classes = SecurityConfigTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("security-config-test")
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost,http://localhost:5173",
        "app.security.swagger-enabled=true",
        "app.security.csp-policy=default-src 'self'",
        "app.frontend-url=http://localhost:5173",
        "app.jwt.secret=test-secret-key-for-testing-only-min-32-characters-long-enough-ok",
        "app.jwt.expiration-ms=86400000",
        "app.jwt.refresh-expiration-ms=604800000"
})
class SecurityConfigTest {

    /**
     * Minimal Spring config that loads only security beans.
     * No database, no Redis, no full application context.
     */
    @Configuration
    @Profile("security-config-test")
    @EnableWebSecurity
    @Import({SecurityConfig.class, CorsConfig.class,
            org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class})
    static class TestConfig {

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            return org.mockito.Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        public UserRepository userRepository() {
            return org.mockito.Mockito.mock(UserRepository.class);
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return org.mockito.Mockito.mock(StringRedisTemplate.class);
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenProvider jwtTokenProvider,
                UserRepository userRepository,
                StringRedisTemplate stringRedisTemplate) {
            return new JwtAuthenticationFilter(jwtTokenProvider, userRepository, stringRedisTemplate);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // Admin endpoint protection
    // =========================================================================
    @Nested
    @DisplayName("Admin endpoints")
    class AdminEndpointTests {

        @Test
        @DisplayName("GET /api/v1/admin/users → rejected without token")
        void adminEndpoint_noToken_rejected() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected admin endpoint to be protected but got " + status;
                    });
        }

        @Test
        @DisplayName("GET /api/v1/admin/stats/overview → rejected without token (nested path)")
        void adminNestedEndpoint_noToken_rejected() throws Exception {
            mockMvc.perform(get("/api/v1/admin/stats/overview")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected admin nested endpoint to be protected but got " + status;
                    });
        }
    }

    // =========================================================================
    // Public endpoints accessibility
    // =========================================================================
    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpointTests {

        @Test
        @DisplayName("GET /api/v1/auth/login → accessible without token (not 401/403)")
        void authLogin_noToken_notForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/auth/login"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // 404 or 405 is fine (no controller loaded), but NOT 401/403
                        assert status != 401 && status != 403
                                : "Expected auth/login to be public but got " + status;
                    });
        }

        @Test
        @DisplayName("GET /api/v1/auth/register → accessible without token (not 401/403)")
        void authRegister_noToken_notForbidden() throws Exception {
            mockMvc.perform(get("/api/v1/auth/register"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status != 401 && status != 403
                                : "Expected auth/register to be public but got " + status;
                    });
        }
    }

    // =========================================================================
    // Authenticated endpoint protection
    // =========================================================================
    @Nested
    @DisplayName("Authenticated endpoints")
    class AuthenticatedEndpointTests {

        @Test
        @DisplayName("GET /api/v1/exams → rejected without token")
        void protectedEndpoint_noToken_rejected() throws Exception {
            mockMvc.perform(get("/api/v1/exams")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected protected endpoint to reject anonymous but got " + status;
                    });
        }

        @Test
        @DisplayName("GET /api/v1/users/me → rejected without token")
        void userProfile_noToken_rejected() throws Exception {
            mockMvc.perform(get("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected user profile to reject anonymous but got " + status;
                    });
        }
    }

    // =========================================================================
    // CORS headers
    // =========================================================================
    @Nested
    @DisplayName("CORS configuration")
    class CorsTests {

        @Test
        @DisplayName("Preflight from allowed origin returns CORS headers")
        void preflight_allowedOrigin_returnsCorsHeaders() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }

        @Test
        @DisplayName("Preflight from disallowed origin is rejected")
        void preflight_disallowedOrigin_rejected() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", "https://evil-site.com")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    // =========================================================================
    // Security headers
    // =========================================================================
    @Nested
    @DisplayName("Security headers")
    class SecurityHeaderTests {

        @Test
        @DisplayName("Response includes Content-Security-Policy header")
        void response_containsCspHeader() throws Exception {
            mockMvc.perform(get("/api/v1/auth/login"))
                    .andExpect(header().exists("Content-Security-Policy"))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'self'"));
        }
    }
}
