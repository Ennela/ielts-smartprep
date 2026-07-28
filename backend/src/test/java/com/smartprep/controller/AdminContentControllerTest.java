package com.smartprep.controller;

import com.smartprep.config.CorsConfig;
import com.smartprep.config.SecurityConfig;
import com.smartprep.dto.response.ContentItemResponse;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Role;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtAuthenticationFilter;
import com.smartprep.security.JwtTokenProvider;
import com.smartprep.service.AdminListeningService;
import com.smartprep.service.AdminService;
import com.smartprep.service.ContentModerationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
        classes = AdminContentControllerTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@ActiveProfiles("admin-content-test")
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost",
        "app.security.swagger-enabled=true",
        "app.security.csp-policy=default-src 'self'",
        "app.frontend-url=http://localhost:5173",
        "app.jwt.secret=test-secret-key-for-testing-only-min-32-characters-long-enough-ok",
        "app.jwt.expiration-ms=86400000",
        "app.jwt.refresh-expiration-ms=604800000"
})
class AdminContentControllerTest {

    @Configuration
    @Profile("admin-content-test")
    @EnableWebSecurity
    @Import({SecurityConfig.class, CorsConfig.class,
            AdminContentController.class, AdminController.class, AdminListeningController.class,
            org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class})
    static class TestConfig {

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            JwtTokenProvider mock = mock(JwtTokenProvider.class);
            // Admin token
            when(mock.validateToken("admin-token")).thenReturn(true);
            when(mock.getUserIdFromToken("admin-token")).thenReturn(1L);
            when(mock.getRoleFromToken("admin-token")).thenReturn("ADMIN");
            when(mock.getTokenTypeFromToken("admin-token")).thenReturn("access");
            when(mock.getJtiFromToken("admin-token")).thenReturn("admin-jti");
            // Student token
            when(mock.validateToken("student-token")).thenReturn(true);
            when(mock.getUserIdFromToken("student-token")).thenReturn(2L);
            when(mock.getRoleFromToken("student-token")).thenReturn("STUDENT");
            when(mock.getTokenTypeFromToken("student-token")).thenReturn("access");
            when(mock.getJtiFromToken("student-token")).thenReturn("student-jti");
            return mock;
        }

        @Bean
        public UserRepository userRepository() {
            UserRepository mock = mock(UserRepository.class);
            User admin = User.builder().userId(1L).username("admin").role(Role.ADMIN).build();
            User student = User.builder().userId(2L).username("student").role(Role.STUDENT).build();
            when(mock.findById(1L)).thenReturn(Optional.of(admin));
            when(mock.findById(2L)).thenReturn(Optional.of(student));
            return mock;
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtTokenProvider jwtTokenProvider,
                UserRepository userRepository,
                StringRedisTemplate stringRedisTemplate) {
            return new JwtAuthenticationFilter(jwtTokenProvider, userRepository, stringRedisTemplate);
        }

        @Bean
        public ContentModerationService contentModerationService() {
            ContentModerationService mock = mock(ContentModerationService.class);
            ContentItemResponse item = ContentItemResponse.builder()
                    .id(1L).type("READING").title("Test")
                    .contentStatus(ContentStatus.PUBLISHED)
                    .createdBy("SYSTEM").createdAt(LocalDateTime.now())
                    .build();
            // Must be a *paged* Page: an unpaged PageImpl exposes Pageable.unpaged(),
            // whose getPageNumber() throws UnsupportedOperationException during Jackson
            // serialization. ContentModerationService always builds one via PageRequest.of.
            Page<ContentItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            when(mock.listContent(any(), any(), anyInt(), anyInt(), any())).thenReturn(page);
            when(mock.updateStatus(any(), anyLong(), any())).thenReturn(item);
            return mock;
        }

        @Bean
        public AdminService adminService() {
            return mock(AdminService.class);
        }

        @Bean
        public AdminListeningService adminListeningService() {
            return mock(AdminListeningService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminListeningService adminListeningService;

    // =========================================================================
    // Authorization tests
    // =========================================================================
    @Nested
    @DisplayName("Authorization")
    class AuthorizationTests {

        @Test
        @DisplayName("GET /api/v1/admin/content → 401 without token")
        void listContent_noToken_unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/admin/content")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected 401/403 but got " + status;
                    });
        }

        @Test
        @DisplayName("GET /api/v1/admin/content → 403 with STUDENT token")
        void listContent_studentToken_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/content")
                            .header("Authorization", "Bearer student-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/v1/admin/content → 200 with ADMIN token")
        void listContent_adminToken_ok() throws Exception {
            mockMvc.perform(get("/api/v1/admin/content")
                            .header("Authorization", "Bearer admin-token")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("PUT /api/v1/admin/content/READING/1/status → 401 without token")
        void updateStatus_noToken_unauthorized() throws Exception {
            mockMvc.perform(put("/api/v1/admin/content/READING/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newStatus\":\"PUBLISHED\"}"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        assert status == 401 || status == 403
                                : "Expected 401/403 but got " + status;
                    });
        }

        @Test
        @DisplayName("PUT /api/v1/admin/content/READING/1/status → 403 with STUDENT token")
        void updateStatus_studentToken_forbidden() throws Exception {
            mockMvc.perform(put("/api/v1/admin/content/READING/1/status")
                            .header("Authorization", "Bearer student-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newStatus\":\"PUBLISHED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("PUT /api/v1/admin/content/READING/1/status → 200 with ADMIN token")
        void updateStatus_adminToken_ok() throws Exception {
            mockMvc.perform(put("/api/v1/admin/content/READING/1/status")
                            .header("Authorization", "Bearer admin-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newStatus\":\"PUBLISHED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("DELETE Reading and Writing content succeeds with ADMIN token")
        void deleteReadingAndWriting_adminToken_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/reading-quizzes/41")
                            .header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Reading quiz template archived"));

            mockMvc.perform(delete("/api/v1/admin/writing-prompts/42")
                            .header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Prompt archived"));

            verify(adminService).deleteReadingQuiz(41L);
            verify(adminService).deleteWritingPrompt(42L);
        }

        @Test
        @DisplayName("DELETE Listening and Mock content succeeds with ADMIN token")
        void deleteListeningAndMock_adminToken_ok() throws Exception {
            mockMvc.perform(delete("/api/v1/admin/listening/parts/43")
                            .header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Listening part archived successfully"));

            mockMvc.perform(delete("/api/v1/admin/mock-tests/44")
                            .header("Authorization", "Bearer admin-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Mock test archived successfully"));

            verify(adminListeningService).deletePart(43L);
            verify(adminService).deleteMockTest(44L);
        }
    }
}
