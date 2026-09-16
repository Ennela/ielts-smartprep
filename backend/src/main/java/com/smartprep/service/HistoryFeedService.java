package com.smartprep.service;

import com.smartprep.dto.response.HistoryFeedItemResponse;
import com.smartprep.service.util.UserPageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * The History page's merged list, paged on the server.
 *
 * <p>It used to call the four per-skill history endpoints for the largest page each would
 * give (100 rows) and merge, filter and page them in the browser -- four requests to show
 * eight rows, and a silent ceiling of 100 sittings per skill. One query now unions the
 * four source tables for the user, applies the page's filters and returns the requested
 * page, newest first.
 *
 * <p>Plain SQL through JdbcTemplate rather than JPQL: a UNION across four entities is
 * not something JPQL expresses, and a native {@code @Query} returning a projection would
 * only add a mapping layer over the same statement. Every branch is a lookup on that
 * table's {@code user_id} index; the sort runs over one user's rows only.
 */
@Service
@RequiredArgsConstructor
public class HistoryFeedService {

    public static final Set<String> SKILLS = Set.of("READING", "LISTENING", "WRITING", "MOCK_TEST");

    // The text columns are collated explicitly: the older tables are utf8mb4_unicode_ci and
    // the newer ones utf8mb4_0900_ai_ci, and MySQL refuses to UNION the two ("Illegal mix of
    // collations"). 0900_ai_ci is the database default, so it costs nothing on those.
    private static final String UNION = """
            SELECT 'READING' AS skill, q.quiz_id AS ref_id,
                   q.topic COLLATE utf8mb4_0900_ai_ci AS title, q.score AS score,
                   NULL AS status, q.submitted_at AS submitted_at
            FROM reading_quizzes q
            WHERE q.user_id = :userId AND q.submitted_at IS NOT NULL AND q.deleted_at IS NULL
            UNION ALL
            SELECT 'LISTENING', t.test_id, t.test_mode COLLATE utf8mb4_0900_ai_ci, t.score,
                   NULL, t.submitted_at
            FROM listening_tests t
            WHERE t.user_id = :userId
            UNION ALL
            SELECT 'WRITING', w.submission_id, p.essay_type COLLATE utf8mb4_0900_ai_ci, w.overall_band,
                   NULL, w.submitted_at
            FROM writing_submissions w JOIN writing_prompts p ON p.prompt_id = w.prompt_id
            WHERE w.user_id = :userId
            UNION ALL
            SELECT 'MOCK_TEST', s.submission_id, m.title COLLATE utf8mb4_0900_ai_ci, s.overall_band,
                   s.status COLLATE utf8mb4_0900_ai_ci, s.submitted_at
            FROM mock_test_submissions s JOIN mock_tests m ON m.mock_test_id = s.mock_test_id
            WHERE s.user_id = :userId
            """;

    private static final String FILTER = """
            WHERE (:skill IS NULL OR h.skill = :skill)
              AND (:from IS NULL OR h.submitted_at >= :from)
              AND (:q IS NULL OR LOWER(h.title) LIKE CONCAT('%', LOWER(:q), '%'))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * @param skill one of {@link #SKILLS}, or null for all
     * @param from  only sittings submitted at or after this moment, or null
     * @param q     case-insensitive substring of the title, or null
     */
    @Transactional(readOnly = true)
    public Page<HistoryFeedItemResponse> feed(Long userId, String skill, LocalDateTime from, String q,
                                              int page, int size) {
        String skillFilter = skill == null || skill.isBlank() ? null : skill.trim().toUpperCase();
        if (skillFilter != null && !SKILLS.contains(skillFilter)) {
            throw new IllegalArgumentException("Invalid skill: " + skill);
        }
        String search = q == null || q.isBlank() ? null : q.trim();
        PageRequest pageRequest = UserPageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("skill", skillFilter)
                .addValue("from", from == null ? null : Timestamp.valueOf(from))
                .addValue("q", search)
                .addValue("limit", pageRequest.getPageSize())
                .addValue("offset", pageRequest.getOffset());

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + UNION + ") h " + FILTER, params, Long.class);
        long count = total == null ? 0 : total;
        List<HistoryFeedItemResponse> rows = count == 0 ? List.of() : jdbc.query(
                "SELECT h.* FROM (" + UNION + ") h " + FILTER
                        + " ORDER BY h.submitted_at DESC, h.ref_id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, i) -> HistoryFeedItemResponse.builder()
                        .skill(rs.getString("skill"))
                        .refId(rs.getLong("ref_id"))
                        .title(rs.getString("title"))
                        .score(rs.getBigDecimal("score"))
                        .status(rs.getString("status"))
                        .submittedAt(rs.getTimestamp("submitted_at").toLocalDateTime())
                        .build());

        return new PageImpl<>(rows, pageRequest, count);
    }
}
