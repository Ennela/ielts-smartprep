package com.smartprep.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for resetting password with a token")
public class ResetPasswordRequest {
    @NotBlank
    @Schema(description = "Password reset token received via email")
    private String token;

    @NotBlank @Size(min = 6, max = 100)
    @Schema(description = "New password (min 6 characters)", example = "NewSecurePassword123")
    private String newPassword;
}
