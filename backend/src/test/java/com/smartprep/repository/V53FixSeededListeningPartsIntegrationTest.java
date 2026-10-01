package com.smartprep.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V53 hides the V30 Listening seed and sends the V6 seed back to be voiced.
 *
 * <p>Migrates to V52, adds rows shaped like the ones the running database has -- a part
 * voiced since, a V30 part repaired by hand -- then runs V53. Those must come through
 * unchanged; only what the seeds left behind may move.
 */
@Tag("integration")
class V53FixSeededListeningPartsIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_v53")
            .withUsername("test")
            .withPassword("test");

    private static long voicedPart;
    private static long repairedCam19Part;
    private static long mockTestOnCam19;

    @BeforeAll
    static void migrateToV52ThenSeedThenRunV53() throws SQLException {
        AbstractMySQLContainerTest.requireDocker();
        MYSQL.start();

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("52")
                .load()
                .migrate();

        try (Connection c = connection()) {
            // A V6-style part whose audio was generated again, as on the running database.
            voicedPart = insert(c, "INSERT INTO listening_parts (part_number, title, audio_url, audio_status, "
                    + "transcript_text, content_status) VALUES (1, 'Voiced', "
                    + "'/api/v1/listening/audio/part_1_1780490224221.mp3', 'READY', 'A: Hello.', 'PUBLISHED')");
            // A V30 part somebody has given a transcript.
            repairedCam19Part = insert(c, "INSERT INTO listening_parts (part_number, title, audio_url, audio_status, "
                    + "transcript_text, created_by, content_status) VALUES (1, 'Repaired', "
                    + "'/api/v1/listening/audio/cam19_test1_part1.mp3', 'READY', 'A: Hello.', 'CAMBRIDGE_19', 'PUBLISHED')");

            long cam19Part = firstLong(c, "SELECT part_id FROM listening_parts "
                    + "WHERE created_by = 'CAMBRIDGE_19' AND transcript_text IS NULL ORDER BY part_id");
            mockTestOnCam19 = insert(c, "INSERT INTO mock_tests (title, difficulty) VALUES ('On Cam19', 'MEDIUM')");
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO mock_test_listening_parts (mock_test_id, part_id, part_order) VALUES ("
                        + mockTestOnCam19 + ", " + cam19Part + ", 1)");
            }
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    @DisplayName("hides all sixteen V30 parts")
    void hidesTheV30Parts() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM listening_parts "
                    + "WHERE created_by = 'CAMBRIDGE_19' AND transcript_text IS NULL"))
                    .as("V30 seeds sixteen").isEqualTo(16);
            assertThat(firstLong(c, "SELECT COUNT(*) FROM listening_parts "
                    + "WHERE created_by = 'CAMBRIDGE_19' AND transcript_text IS NULL AND deleted_at IS NULL"))
                    .isZero();
        }
    }

    @Test
    @DisplayName("leaves a V30 part that has been given a transcript")
    void keepsRepairedCam19Part() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM listening_parts WHERE part_id = " + repairedCam19Part
                    + " AND deleted_at IS NULL")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("sends the eight V6 parts back to PENDING, so they are voiced")
    void v6PartsArePending() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM listening_parts "
                    + "WHERE audio_url REGEXP '^/api/v1/listening/audio/part[1-4][ab][.]mp3$' "
                    + "AND audio_status = 'PENDING' AND transcript_text IS NOT NULL AND deleted_at IS NULL"))
                    .isEqualTo(8);
        }
    }

    @Test
    @DisplayName("leaves a part whose audio was really generated READY")
    void keepsVoicedPartReady() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM listening_parts WHERE part_id = " + voicedPart
                    + " AND audio_status = 'READY'")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("hides a mock test built on a V30 part, and keeps the V14 one built on V6")
    void hidesOnlyMockTestsOnCam19() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM mock_tests WHERE mock_test_id = " + mockTestOnCam19
                    + " AND deleted_at IS NOT NULL")).isEqualTo(1);
            assertThat(firstLong(c, "SELECT COUNT(*) FROM mock_tests m "
                    + "JOIN mock_test_listening_parts ml ON ml.mock_test_id = m.mock_test_id "
                    + "JOIN listening_parts p ON p.part_id = ml.part_id "
                    + "WHERE p.audio_url = '/api/v1/listening/audio/part1a.mp3' AND m.deleted_at IS NULL"))
                    .as("the V14 mock test uses V6 parts and stays").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("leaves no visible mock test containing a hidden part")
    void noVisibleMockTestContainsAHiddenPart() throws SQLException {
        try (Connection c = connection()) {
            assertThat(firstLong(c, "SELECT COUNT(*) FROM mock_tests m "
                    + "JOIN mock_test_listening_parts ml ON ml.mock_test_id = m.mock_test_id "
                    + "JOIN listening_parts p ON p.part_id = ml.part_id "
                    + "WHERE m.deleted_at IS NULL AND p.deleted_at IS NOT NULL")).isZero();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long insert(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet rs = s.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static long firstLong(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
