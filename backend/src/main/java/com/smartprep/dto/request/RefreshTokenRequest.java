package com.smartprep.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for refreshing access token")
public class RefreshTokenRequest {
    @NotBlank
    @Schema(description = "Refresh token obtained during login", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;
}
