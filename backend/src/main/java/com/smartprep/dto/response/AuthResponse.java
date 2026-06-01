package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class AuthResponse {
    private Long userId;
    private String username;
    private String displayName;
    private String email;
    private String token;
    private Long expiresIn;
    private String role;
    private BigDecimal targetReadingScore;
    private BigDecimal targetWritingScore;
    private BigDecimal targetListeningScore;
}
