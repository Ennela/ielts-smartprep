package com.smartprep.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for user registration")
public class RegisterRequest {
    @NotBlank @Email
    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @NotBlank @Size(min = 3, max = 50)
    @Schema(description = "Desired username", example = "john_doe")
    private String username;

    @NotBlank @Size(min = 6, max = 100)
    @Schema(description = "Account password (min 6 characters)", example = "SecurePassword123")
    private String password;
}
