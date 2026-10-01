package com.smartprep.controller;

import com.smartprep.config.CorsConfig;
import com.smartprep.config.SecurityConfig;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtAuthenticationFilter;
import com.smartprep.security.JwtTokenProvider;
import com.smartprep.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Content images: admins upload, anyone viewing the question can load them, and nothing but
 * an image this controller stored is ever served from the route.
 */
@SpringBootTest(classes = ImageControllerTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("image-controller-test")
@TestPropertySource(properties = {
        "app.cors.allowed-origins=http://localhost",
        "app.security.swagger-enabled=false",
        "app.security.csp-policy=default-src 'self'",
        "app.frontend-url=http://localhost:5173",
        "app.jwt.secret=test-secret-key-for-testing-only-min-32-characters-long-enough-ok",
        "app.jwt.expiration-ms=86400000",
        "app.jwt.refresh-expiration-ms=604800000"
})
class ImageControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String KEY = "img_0b8f8f4e-2a7d-4c43-9a51-5d1c3f0e6a21.png";

    @Configuration
    @Profile("image-controller-test")
    @EnableWebSecurity
    @Import({SecurityConfig.class, CorsConfig.class, ImageController.class,
            com.smartprep.exception.GlobalExceptionHandler.class,
            org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration.class,
            org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration.class,
            org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class})
    static class TestConfig {

        @Bean
        public JwtTokenProvider jwtTokenProvider() {
            JwtTokenProvider mock = mock(JwtTokenProvider.class);
            for (Object[] t : new Object[][] {{"admin-token", 1L, "ADMIN"}, {"student-token", 2L, "STUDENT"}}) {
                String token = (String) t[0];
                when(mock.validateToken(token)).thenReturn(true);
                when(mock.getUserIdFromToken(token)).thenReturn((Long) t[1]);
                when(mock.getRoleFromToken(token)).thenReturn((String) t[2]);
                when(mock.getTokenTypeFromToken(token)).thenReturn("access");
                when(mock.getJtiFromToken(token)).thenReturn(token + "-jti");
            }
            return mock;
        }

        @Bean
        public UserRepository userRepository() {
            UserRepository mock = mock(UserRepository.class);
            when(mock.findById(1L)).thenReturn(Optional.of(User.builder().userId(1L).username("admin").role(Role.ADMIN).build()));
            when(mock.findById(2L)).thenReturn(Optional.of(User.builder().userId(2L).username("student").role(Role.STUDENT).build()));
            return mock;
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider p, UserRepository u, StringRedisTemplate r) {
            return new JwtAuthenticationFilter(p, u, r);
        }

        @Bean
        public StorageService storageService() {
            return mock(StorageService.class);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private StorageService storageService;

    @BeforeEach
    void resetStorage() {
        Mockito.reset(storageService);
    }

    @Test
    @DisplayName("an admin uploads a PNG and gets back its URL on this site")
    void adminUploads() throws Exception {
        when(storageService.uploadContentImage(anyString(), any(), anyString()))
                .thenAnswer(inv -> "/api/v1/images/" + inv.getArgument(0));

        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "map.png", "image/png", PNG))
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.matchesPattern(
                        "/api/v1/images/img_[0-9a-f-]{36}\\.png")));
        verify(storageService).uploadContentImage(matches("img_[0-9a-f-]{36}\\.png"), eq(PNG), eq("image/png"));
    }

    @Test
    @DisplayName("a file that is not an image is refused, whatever it is called")
    void refusesNonImage() throws Exception {
        byte[] page = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "chart.png", "image/png", page))
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isBadRequest());
        verify(storageService, never()).uploadContentImage(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("a learner cannot upload")
    void learnerCannotUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "map.png", "image/png", PNG))
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an uploaded image is served without signing in, with its type")
    void servesImage() throws Exception {
        when(storageService.downloadAudio(KEY)).thenReturn(PNG);

        mockMvc.perform(get("/api/v1/images/" + KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")))
                .andExpect(content().bytes(PNG));
    }

    @Test
    @DisplayName("nothing else in the bucket is reachable through the route")
    void servesOnlyItsOwnKeys() throws Exception {
        for (String other : new String[] {"part_1_1780490224221.mp3", "avatar_2_1790263794339.png", "writing_prompt_89.jpg"}) {
            mockMvc.perform(get("/api/v1/images/" + other)).andExpect(status().isNotFound());
        }
        verify(storageService, never()).downloadAudio(anyString());
    }
}
