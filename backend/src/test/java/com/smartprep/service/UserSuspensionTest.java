package com.smartprep.service;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.exception.AccountSuspendedException;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A suspended account gets no new session: login is refused after the password check, and
 * the refresh endpoint refuses it after revoking the presented refresh token.
 */
@ExtendWith(MockitoExtension.class)
class UserSuspensionTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StatsService statsService;
    @Mock private TokenService tokenService;
    @Mock private LoginLockoutService loginLockoutService;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private UserService userService;

    private User suspended;

    @BeforeEach
    void setUp() {
        suspended = User.builder()
                .userId(3L).username("blocked").email("blocked@test.com")
                .passwordHash("$2a$hash").role(Role.STUDENT).suspended(true)
                .build();
    }

    @Test
    @DisplayName("refuses to log in a suspended account with the right password")
    void loginRefused() {
        LoginRequest request = new LoginRequest();
        request.setUsername("blocked");
        request.setPassword("correct");
        when(loginLockoutService.isLocked("blocked")).thenReturn(false);
        when(userRepository.findByUsername("blocked")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("correct", "$2a$hash")).thenReturn(true);

        assertThrows(AccountSuspendedException.class, () -> userService.login(request));
        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString(), anyString());
        verify(tokenService, never()).storeRefreshToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("ends a suspended user's session at the next refresh")
    void refreshRefused() {
        when(tokenService.validateRefreshToken("refresh")).thenReturn(3L);
        when(jwtTokenProvider.getJtiFromToken("refresh")).thenReturn("jti-1");
        when(userRepository.findById(3L)).thenReturn(Optional.of(suspended));

        assertThrows(AccountSuspendedException.class, () -> userService.refreshToken("refresh"));
        verify(tokenService).revokeRefreshToken("jti-1");
        verify(jwtTokenProvider, never()).generateRefreshToken(anyLong());
    }
}
