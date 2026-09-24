package com.smartprep.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V48 is a data migration, so it has to be exercised on data that predates it.
 *
 * <p>The shared container used by the other integration tests has already been migrated
 * to the latest version by the time any test runs, and on a fresh schema V48 finds
 * nothing to backfill. This test therefore drives Flyway itself on its own container:
 * migrate to V47, insert the kind of rows a live database had before the link column
 * existed, then apply V48 and look at what it wrote.
 */
@Tag("integration")
class V48BackfillMigrationIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_backfill")
            .withUsername("test")
            .withPassword("test");

    private static long completedSubmission;
    private static long failedSubmission;
    private static final Timestamp COMPLETED_AT = Timestamp.valueOf(LocalDateTime.of(2026, 8, 1, 9, 30));
    private static final Timestamp FAILED_AT = Timestamp.valueOf(LocalDateTime.of(2026, 8, 15, 14, 0));

    @BeforeAll
    static void migrateToV47ThenSeedThenBackfill() throws SQLException {
        AbstractMySQLContainerTest.requireDocker();
        MYSQL.start();

        Flyway toV47 = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("47")
                .load();
        toV47.migrate();

        try (Connection c = connection()) {
            long userId = insertReturningId(c,
                    "INSERT INTO users (email, username, password_hash, display_name, role) VALUES ('bf@example.test', 'backfill_user', 'x', 'Backfill', 'STUDENT')");
            long sessionA = insertReturningId(c,
                    "INSERT INTO mock_test_sessions (user_id, mock_test_id, status, current_section, time_remaining_seconds, progress_json) VALUES ("
                            + userId + ", 1, 'SUBMITTED', 'WRITING', 0, '{}')");
            long sessionB = insertReturningId(c,
                    "INSERT INTO mock_test_sessions (user_id, mock_test_id, status, current_section, time_remaining_seconds, progress_json) VALUES ("
                            + userId + ", 1, 'SUBMITTED', 'WRITING', 0, '{}')");

            // A graded sitting: all three bands are real.
            completedSubmission = insertSubmission(c, userId, sessionA, "COMPLETED", "6.0", "5.5", "6.5", "6.0", COMPLETED_AT);
            // A sitting whose writing grade failed: listening and reading are real, writing
            // is the placeholder zero.
            failedSubmission = insertSubmission(c, userId, sessionB, "FAILED", "7.0", "6.5", "0.0", "0.0", FAILED_AT);

            // Nothing referenced these sittings before V47 existed.
            assertThat(count(c, "SELECT COUNT(*) FROM score_history")).isZero();
        }

        Flyway latest = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load();
        latest.migrate();
        // That V48 ran, not that it is the newest migration in the repository. The original
        // assertion pinned the latest version to "48", so every migration added afterwards
        // failed this test for a reason that has nothing to do with the backfill it covers.
        assertThat(latest.info().applied())
                .extracting(info -> info.getVersion() == null ? null : info.getVersion().getVersion())
                .contains("48");
    }

    @Test
    @DisplayName("a completed sitting gets Listening, Reading and Writing rows dated at its submission")
    void completedSittingGetsThreeRows() throws SQLException {
        try (Connection c = connection()) {
            Map<String, Row> rows = rowsFor(c, completedSubmission);

            assertThat(rows.keySet()).containsExactlyInAnyOrder("LISTENING", "READING", "WRITING");
            assertThat(rows.get("LISTENING").score).isEqualByComparingTo("6.0");
            assertThat(rows.get("READING").score).isEqualByComparingTo("5.5");
            assertThat(rows.get("WRITING").score).isEqualByComparingTo("6.5");
            rows.values().forEach(r -> {
                assertThat(r.difficulty).isEqualTo("MOCK_TEST");
                assertThat(r.recordedAt).isEqualTo(COMPLETED_AT);
            });
            // Mock test 1 has no reading passages linked, so the module type falls back.
            assertThat(rows.get("READING").moduleType).isEqualTo("ACADEMIC");
        }
    }

    @Test
    @DisplayName("a sitting without a writing grade gets Listening and Reading only -- no placeholder zero")
    void failedSittingGetsTwoRows() throws SQLException {
        try (Connection c = connection()) {
            Map<String, Row> rows = rowsFor(c, failedSubmission);

            assertThat(rows.keySet()).containsExactlyInAnyOrder("LISTENING", "READING");
            assertThat(rows.get("LISTENING").score).isEqualByComparingTo("7.0");
            assertThat(rows.get("READING").recordedAt).isEqualTo(FAILED_AT);
        }
    }

    @Test
    @DisplayName("the backfill wrote nothing it was not asked for")
    void nothingElseWritten() throws SQLException {
        try (Connection c = connection()) {
            assertThat(count(c, "SELECT COUNT(*) FROM score_history")).isEqualTo(5);
            assertThat(count(c, "SELECT COUNT(*) FROM score_history WHERE mock_test_submission_id IS NULL")).isZero();
        }
    }

    // ------------------------------------------------------------------ JDBC helpers

    private record Row(BigDecimal score, String difficulty, String moduleType, Timestamp recordedAt) {}

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long insertSubmission(Connection c, long userId, long sessionId, String status,
                                         String listening, String reading, String writing, String overall,
                                         Timestamp submittedAt) throws SQLException {
        String sql = "INSERT INTO mock_test_submissions (user_id, mock_test_id, session_id, status, listening_score, "
                + "reading_score, writing_score, overall_band, listening_correct_answers, reading_correct_answers, submitted_at) "
                + "VALUES (?, 1, ?, ?, ?, ?, ?, ?, 0, 0, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setLong(2, sessionId);
            ps.setString(3, status);
            ps.setBigDecimal(4, new BigDecimal(listening));
            ps.setBigDecimal(5, new BigDecimal(reading));
            ps.setBigDecimal(6, new BigDecimal(writing));
            ps.setBigDecimal(7, new BigDecimal(overall));
            ps.setTimestamp(8, submittedAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long insertReturningId(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static Map<String, Row> rowsFor(Connection c, long submissionId) throws SQLException {
        Map<String, Row> rows = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT skill_type, score, difficulty, module_type, recorded_at FROM score_history WHERE mock_test_submission_id = ?")) {
            ps.setLong(1, submissionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.put(rs.getString(1), new Row(rs.getBigDecimal(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5)));
                }
            }
        }
        return rows;
    }
}
