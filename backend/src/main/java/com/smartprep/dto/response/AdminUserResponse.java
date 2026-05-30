package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data @Builder
public class AdminUserResponse {
    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private BigDecimal targetReadingScore;
    private BigDecimal targetWritingScore;
    private BigDecimal targetListeningScore;
    private long totalTests;
    private BigDecimal avgScore;
    private LocalDateTime createdAt;
}
