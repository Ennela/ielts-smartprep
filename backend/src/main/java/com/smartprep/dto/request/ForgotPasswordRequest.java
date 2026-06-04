package com.smartprep.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for forgot password")
public class ForgotPasswordRequest {
    @NotBlank @Email
    @Schema(description = "Email address associated with the account", example = "john.doe@example.com")
    private String email;
}
