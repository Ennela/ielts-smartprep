package com.smartprep.security;

import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserRepository userRepository;
    @Mock private StringRedisTemplate redisTemplate;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, userRepository, redisTemplate);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("should authenticate when JWT role claim matches persisted user role")
    void matchingRoleClaim_authenticates() throws Exception {
        stubValidAccessToken("token", "jti", 1L, "STUDENT");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.STUDENT)));

        filter.doFilter(requestWithBearer("token"), new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("should reject when JWT role claim does not match persisted user role")
    void mismatchedRoleClaim_rejected() throws Exception {
        stubValidAccessToken("token", "jti", 1L, "ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(Role.STUDENT)));

        filter.doFilter(requestWithBearer("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("should reject when JWT role claim is missing")
    void missingRoleClaim_rejected() throws Exception {
        stubValidAccessToken("token", "jti", 1L, null);

        filter.doFilter(requestWithBearer("token"), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findById(anyLong());
    }

    private void stubValidAccessToken(String token, String jti, Long userId, String role) {
        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getTokenTypeFromToken(token)).thenReturn("access");
        when(jwtTokenProvider.getJtiFromToken(token)).thenReturn(jti);
        when(redisTemplate.hasKey("blacklist:jti:" + jti)).thenReturn(false);
        when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(jwtTokenProvider.getRoleFromToken(token)).thenReturn(role);
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private User user(Role role) {
        return User.builder()
                .userId(1L)
                .username("testuser")
                .role(role)
                .build();
    }
}
