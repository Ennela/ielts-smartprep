package com.smartprep.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.AbstractMySQLContainerTest;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.LoginLockoutService;
import com.smartprep.service.ai.MockTestAsyncGrader;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * A signed-in learner asking for an admin endpoint gets 403, with the API's JSON body.
 *
 * <p>It used to get 401 with no body. Spring's default access-denied handler calls
 * {@code sendError(403)}; the servlet container then forwards to {@code /error}, the JWT
 * filter does not run on that error dispatch, so {@code /error} saw an anonymous request
 * and the entry point answered 401 -- which also tells the frontend to refresh a token that
 * is fine. A role check made by {@code @PreAuthorize} inside a controller fell through to
 * the catch-all exception handler instead, as a 500.
 *
 * <p>Runs on a real port: MockMvc does not perform the error dispatch, so it shows 403 even
 * when a browser gets 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AdminAccessIntegrationTest extends AbstractMySQLContainerTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;

    // The JWT filter checks the logout blacklist in Redis; there is no Redis here.
    @MockBean private StringRedisTemplate redisTemplate;
    @MockBean private MockTestAsyncGrader asyncGrader;
    @MockBean private ProxyManager<String> proxyManager;
    @MockBean private LoginLockoutService loginLockoutService;
    // generate-audio is metered by the AI rate limit, which needs Redis too.
    @MockBean private RateLimitInterceptor rateLimitInterceptor;

    private String learnerToken;
    private String adminToken;

    @BeforeEach
    void users() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        String tag = UUID.randomUUID().toString().substring(0, 8);
        User learner = userRepository.save(User.builder().username("access_" + tag).passwordHash("x")
                .email("access_" + tag + "@example.test").role(Role.STUDENT).build());
        User admin = userRepository.save(User.builder().username("access_admin_" + tag).passwordHash("x")
                .email("access_admin_" + tag + "@example.test").role(Role.ADMIN).build());
        learnerToken = jwtTokenProvider.generateAccessToken(learner.getUserId(), learner.getUsername(), "STUDENT");
        adminToken = jwtTokenProvider.generateAccessToken(admin.getUserId(), admin.getUsername(), "ADMIN");
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    private void assertAccessDenied(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("success").asBoolean(true)).isFalse();
        assertThat(body.path("errorCode").asText()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("a learner on an admin path gets 403 with a JSON body")
    void learnerOnAdminPath() throws Exception {
        assertAccessDenied(call(HttpMethod.GET, "/api/v1/admin/users", learnerToken));
    }

    @Test
    @DisplayName("a learner on an admin-only action outside /admin gets 403, not 500")
    void learnerOnAdminOnlyMethod() throws Exception {
        assertAccessDenied(call(HttpMethod.POST, "/api/v1/listening/1/generate-audio", learnerToken));
    }

    @Test
    @DisplayName("without a token an admin path is still 401, and an admin still gets in")
    void anonymousAndAdmin() {
        assertThat(call(HttpMethod.GET, "/api/v1/admin/users", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/api/v1/admin/users", adminToken).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
