package com.smartprep.service;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RegisterRequest;
import com.smartprep.dto.response.AuthResponse;
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
        @DisplayName("should register successfully with valid input")
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
            when(jwtTokenProvider.generateToken(eq(1L), eq("testuser"), eq("STUDENT")))
                    .thenReturn("jwt-token");

            AuthResponse response = userService.register(request);

            assertNotNull(response);
            assertEquals("testuser", response.getUsername());
            assertEquals("test@mail.com", response.getEmail());
            assertEquals("jwt-token", response.getToken());
            verify(userRepository).save(any(User.class));
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
                    .targetReadingScore(new BigDecimal("6.5"))
                    .targetWritingScore(new BigDecimal("6.5"))
                    .targetListeningScore(new BigDecimal("6.5"))
                    .build();
        }

        @Test
        @DisplayName("should login successfully with correct credentials")
        void login_success() {
            LoginRequest request = createLoginRequest("testuser", "password123");

            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
            when(jwtTokenProvider.generateToken(eq(1L), eq("testuser"), eq("STUDENT")))
                    .thenReturn("jwt-token");

            AuthResponse response = userService.login(request);

            assertNotNull(response);
            assertEquals(1L, response.getUserId());
            assertEquals("jwt-token", response.getToken());
        }

        @Test
        @DisplayName("should throw on wrong password")
        void login_wrongPassword() {
            LoginRequest request = createLoginRequest("testuser", "wrongpass");
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrongpass", "$2a$encoded")).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> userService.login(request));
        }

        @Test
        @DisplayName("should throw on non-existent username")
        void login_userNotFound() {
            LoginRequest request = createLoginRequest("ghost", "pass");
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> userService.login(request));
        }
    }
}
