package com.smartprep.controller;

import com.smartprep.dto.request.LoginRequest;
import com.smartprep.dto.request.RegisterRequest;
import com.smartprep.dto.response.ApiResponse;
import com.smartprep.dto.response.AuthResponse;
import com.smartprep.model.entity.User;
import com.smartprep.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Registration successful"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> getProfile(@AuthenticationPrincipal User user) {
        AuthResponse response = userService.getProfile(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
