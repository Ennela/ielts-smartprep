package com.smartprep.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
@Schema(description = "Response containing authenticated user profile details and JWT token")
public class AuthResponse {
    @Schema(description = "User unique ID", example = "1")
    private Long userId;

    @Schema(description = "User account username", example = "john_doe")
    private String username;

    @Schema(description = "User display name", example = "John Doe")
    private String displayName;

    @Schema(description = "User email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "JWT access token for bearer authentication", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "Token expiration duration in milliseconds", example = "86400000")
    private Long expiresIn;

    @Schema(description = "User role", example = "STUDENT")
    private String role;

    @Schema(description = "Target score for Reading section", example = "7.5")
    private BigDecimal targetReadingScore;

    @Schema(description = "Target score for Writing section", example = "6.5")
    private BigDecimal targetWritingScore;

    @Schema(description = "Target score for Listening section", example = "7.0")
    private BigDecimal targetListeningScore;
}
