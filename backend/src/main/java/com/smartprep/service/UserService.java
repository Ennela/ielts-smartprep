package com.smartprep.service;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RegisterRequest;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.repository.UserRepository;
import com.smartprep.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getUsername())
                .targetReadingScore(new BigDecimal("6.5"))
                .targetWritingScore(new BigDecimal("6.5"))
                .targetListeningScore(new BigDecimal("6.5"))
                .build();

        user = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRole().name());

        return buildAuthResponse(user, token);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRole().name());
        return buildAuthResponse(user, token);
    }

    public AuthResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return buildAuthResponse(user, null);
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .token(token)
                .expiresIn(token != null ? 86400000L : null)
                .role(user.getRole() != null ? user.getRole().name() : "STUDENT")
                .targetReadingScore(user.getTargetReadingScore())
                .targetWritingScore(user.getTargetWritingScore())
                .targetListeningScore(user.getTargetListeningScore())
                .build();
    }
}
