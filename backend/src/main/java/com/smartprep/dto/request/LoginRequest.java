package com.smartprep.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for user login")
public class LoginRequest {
    @NotBlank
    @Schema(description = "Account username", example = "john_doe")
    private String username;

    @NotBlank
    @Schema(description = "Account password", example = "SecurePassword123")
    private String password;
}
