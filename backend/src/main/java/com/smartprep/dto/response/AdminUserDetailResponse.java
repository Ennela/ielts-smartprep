package com.smartprep.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class AdminUserDetailResponse {
    private Long userId;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private BigDecimal targetReadingScore;
    private BigDecimal targetWritingScore;
    private BigDecimal targetListeningScore;
    private LocalDateTime createdAt;

    // Per-skill stats
    private List<SkillStat> skillStats;
    // Recent score history
    private List<RecentScore> recentScores;

    @Data @Builder
    public static class SkillStat {
        private String skill;
        private long totalTests;
        private BigDecimal avgScore;
    }

    @Data @Builder
    public static class RecentScore {
        private String skillType;
        private BigDecimal score;
        private LocalDateTime recordedAt;
    }
}
