package com.smartprep.service;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RegisterRequest;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.AccountLockedException;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private StatsService statsService;
    @Mock private TokenService tokenService;
    @Mock private LoginLockoutService loginLockoutService;
    @Mock private EmailVerificationService emailVerificationService;

    @InjectMocks private UserService userService;

    private RegisterRequest createRegisterRequest(String email, String username, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private LoginRequest createLoginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    @Nested
    @DisplayName("Register")
    class RegisterTests {

        @Test
        @DisplayName("should register successfully and return access + refresh tokens")
        void register_success() {
            RegisterRequest request = createRegisterRequest("test@mail.com", "testuser", "password123");

            when(userRepository.existsByUsername("testuser")).thenReturn(false);
            when(userRepository.existsByEmail("test@mail.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setUserId(1L);
                return u;
            });
            when(jwtTokenProvider.generateAccessToken(eq(1L), eq("testuser"), eq("STUDENT")))
                    .thenReturn("access-jwt");
            when(jwtTokenProvider.generateRefreshToken(eq(1L)))
                    .thenReturn("refresh-jwt");
            when(jwtTokenProvider.getJtiFromToken("refresh-jwt")).thenReturn("jti-123");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = userService.register(request);

            assertNotNull(response);
            assertEquals("testuser", response.getUsername());
            assertEquals("test@mail.com", response.getEmail());
            assertEquals("access-jwt", response.getToken());
            assertEquals("refresh-jwt", response.getRefreshToken());
            verify(userRepository).save(any(User.class));
            verify(tokenService).storeRefreshToken("jti-123", 1L);
            verify(emailVerificationService).createVerificationToken(1L, "test@mail.com");
        }

        @Test
        @DisplayName("should throw when username already exists")
        void register_duplicateUsername() {
            RegisterRequest request = createRegisterRequest("new@mail.com", "existing", "pass");
            when(userRepository.existsByUsername("existing")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.register(request));
            assertEquals("Username already exists", ex.getMessage());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when email already exists")
        void register_duplicateEmail() {
            RegisterRequest request = createRegisterRequest("existing@mail.com", "newuser", "pass");
            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("existing@mail.com")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> userService.register(request));
            assertEquals("Email already exists", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        private User existingUser;

        @BeforeEach
        void setUp() {
            existingUser = User.builder()
                    .userId(1L)
                    .username("testuser")
                    .email("test@mail.com")
                    .passwordHash("$2a$encoded")
                    .displayName("testuser")
                    .role(Role.STUDENT)
                    .emailVerified(true)
                    .targetReadingScore(new BigDecimal("6.5"))
                    .targetWritingScore(new BigDecimal("6.5"))
                    .targetListeningScore(new BigDecimal("6.5"))
                    .build();
        }

        @Test
        @DisplayName("should login successfully with correct credentials and return tokens")
        void login_success() {
            LoginRequest request = createLoginRequest("testuser", "password123");

            when(loginLockoutService.isLocked("testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
            when(jwtTokenProvider.generateAccessToken(eq(1L), eq("testuser"), eq("STUDENT")))
                    .thenReturn("access-jwt");
            when(jwtTokenProvider.generateRefreshToken(eq(1L)))
                    .thenReturn("refresh-jwt");
            when(jwtTokenProvider.getJtiFromToken("refresh-jwt")).thenReturn("jti-456");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = userService.login(request);

            assertNotNull(response);
            assertEquals(1L, response.getUserId());
            assertEquals("access-jwt", response.getToken());
            assertEquals("refresh-jwt", response.getRefreshToken());
            verify(loginLockoutService).resetAttempts("testuser");
            verify(tokenService).storeRefreshToken("jti-456", 1L);
        }

        @Test
        @DisplayName("should throw on wrong password and record failed attempt")
        void login_wrongPassword_recordsAttempt() {
            LoginRequest request = createLoginRequest("testuser", "wrongpass");
            when(loginLockoutService.isLocked("testuser")).thenReturn(false);
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongpass", "$2a$encoded")).thenReturn(false);
            when(loginLockoutService.getRemainingAttempts("testuser")).thenReturn(4);

            assertThrows(IllegalArgumentException.class,
                    () -> userService.login(request));
            verify(loginLockoutService).recordFailedAttempt("testuser");
        }

        @Test
        @DisplayName("should throw AccountLockedException when account is locked")
        void login_lockedAccount() {
            LoginRequest request = createLoginRequest("testuser", "password123");
            when(loginLockoutService.isLocked("testuser")).thenReturn(true);
            when(loginLockoutService.getRemainingLockoutSeconds("testuser")).thenReturn(600L);
            when(loginLockoutService.getMaxAttempts()).thenReturn(5);

            assertThrows(AccountLockedException.class,
                    () -> userService.login(request));
            verify(userRepository, never()).findByUsername(any());
        }

        @Test
        @DisplayName("should throw on non-existent username")
        void login_userNotFound() {
            LoginRequest request = createLoginRequest("ghost", "pass");
            when(loginLockoutService.isLocked("ghost")).thenReturn(false);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> userService.login(request));
        }
    }

    @Nested
    @DisplayName("Refresh Token")
    class RefreshTokenTests {

        @Test
        @DisplayName("should rotate tokens on valid refresh")
        void refreshToken_success() {
            String oldRefresh = "old.refresh.token";
            User user = User.builder()
                    .userId(1L).username("testuser").email("t@m.com")
                    .displayName("testuser").role(Role.STUDENT).emailVerified(true)
                    .targetReadingScore(new BigDecimal("6.5"))
                    .targetWritingScore(new BigDecimal("6.5"))
                    .targetListeningScore(new BigDecimal("6.5"))
                    .build();

            when(tokenService.validateRefreshToken(oldRefresh)).thenReturn(1L);
            when(jwtTokenProvider.getJtiFromToken(oldRefresh)).thenReturn("old-jti");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken(1L, "testuser", "STUDENT")).thenReturn("new-access");
            when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh");
            when(jwtTokenProvider.getJtiFromToken("new-refresh")).thenReturn("new-jti");
            when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);

            AuthResponse response = userService.refreshToken(oldRefresh);

            assertEquals("new-access", response.getToken());
            assertEquals("new-refresh", response.getRefreshToken());
            verify(tokenService).revokeRefreshToken("old-jti");
            verify(tokenService).storeRefreshToken("new-jti", 1L);
        }
    }

    @Nested
    @DisplayName("Logout")
    class LogoutTests {

        @Test
        @DisplayName("should revoke refresh token on logout")
        void logout_revokesToken() {
            String refreshToken = "refresh.to.revoke";
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJtiFromToken(refreshToken)).thenReturn("jti-revoke");

            userService.logout(refreshToken);

            verify(tokenService).revokeRefreshToken("jti-revoke");
        }

        @Test
        @DisplayName("should not throw when refresh token is already invalid")
        void logout_invalidToken_noop() {
            when(jwtTokenProvider.validateToken("expired")).thenReturn(false);

            assertDoesNotThrow(() -> userService.logout("expired"));
            verify(tokenService, never()).revokeRefreshToken(any());
        }
    }
}
