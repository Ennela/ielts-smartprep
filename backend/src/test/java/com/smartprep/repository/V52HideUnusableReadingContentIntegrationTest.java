package com.smartprep.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V52 hides Reading passages that cannot be read, and the mock tests built from them.
 *
 * <p>Migrates to V51, seeds the cases that matter, then runs V52. Most of these tests are
 * about what V52 must <em>not</em> touch: a migration that hides good content, or a
 * learner's recorded result, would do more damage than the placeholders it removes.
 */
@Tag("integration")
class V52HideUnusableReadingContentIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ielts_smartprep_v52")
            .withUsername("test")
            .withPassword("test");

    /** Real prose, including hyphenated words, which must not look like the corruption. */
    private static final String REAL_PASSAGE = ("A well-known study of state-of-the-art rackets found that "
            + "the frame, once made of wood, is now a carbon-fibre composite. ").repeat(60);

    /**
     * How the broken import stored "In 2016, the British professional tennis player".
     *
     * <p>Deliberately without the V32 prefix and well over 200 characters, so it can only be
     * caught by the corruption signature. A short string starting with the prefix would be
     * caught by the placeholder rule instead, and the regex would go untested.
     */
    private static final String CORRUPTED_PASSAGE =
            "-I-n- -2-0-1-6-,- -t-h-e- -B-r-i-t-i-s-h- -p-r-o-f-e-s-s-i-o-n-a-l- -t-e-n-n-i-s- -p-l-a-y-e-r- ".repeat(5);

    private static long cleanQuiz;
    private static long corruptedUnsubmitted;
    private static long corruptedSubmitted;
    private static long cleanMockTest;
    private static long brokenMockTest;

    @BeforeAll
    static void migrateToV51ThenSeedThenRunV52() throws SQLException {
        AbstractMySQLContainerTest.requireDocker();
        MYSQL.start();

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .target("51")
                .load()
                .migrate();

        try (Connection c = connection()) {
            long userId = insert(c, "INSERT INTO users (email, username, password_hash, display_name, role) "
                    + "VALUES ('v52@example.test', 'v52_user', 'x', 'V52', 'STUDENT')");

            cleanQuiz = insertQuiz(c, userId, REAL_PASSAGE, false);
            corruptedUnsubmitted = insertQuiz(c, userId, CORRUPTED_PASSAGE, false);
            corruptedSubmitted = insertQuiz(c, userId, CORRUPTED_PASSAGE, true);

            cleanMockTest = insert(c, "INSERT INTO mock_tests (title, difficulty) VALUES ('Clean', 'MEDIUM')");
            link(c, cleanMockTest, cleanQuiz, 1);

            brokenMockTest = insert(c, "INSERT INTO mock_tests (title, difficulty) VALUES ('Broken', 'MEDIUM')");
            link(c, brokenMockTest, cleanQuiz, 1);
            link(c, brokenMockTest, corruptedUnsubmitted, 2);
        }

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    @DisplayName("hides every V32 placeholder, so a fresh database serves none of them")
    void hidesTheV32Placeholders() throws SQLException {
        try (Connection c = connection()) {
            long placeholders = count(c, "SELECT COUNT(*) FROM reading_quizzes "
                    + "WHERE passage_text LIKE '[Cambridge 19 Test %' AND CHAR_LENGTH(passage_text) < 200");
            long stillVisible = count(c, "SELECT COUNT(*) FROM reading_quizzes "
                    + "WHERE passage_text LIKE '[Cambridge 19 Test %' AND CHAR_LENGTH(passage_text) < 200 "
                    + "AND deleted_at IS NULL");

            assertThat(placeholders).as("V32 seeds twelve").isEqualTo(12);
            assertThat(stillVisible).isZero();
        }
    }

    @Test
    @DisplayName("hides an unsubmitted corrupted passage")
    void hidesCorruptedPassage() throws SQLException {
        assertThat(isDeleted(corruptedUnsubmitted)).isTrue();
    }

    @Test
    @DisplayName("leaves a real passage alone, hyphenated words and all")
    void keepsRealPassage() throws SQLException {
        assertThat(isDeleted(cleanQuiz)).isFalse();
    }

    @Test
    @DisplayName("leaves a submitted passage alone, because a result still reads it")
    void keepsSubmittedPassage() throws SQLException {
        assertThat(isDeleted(corruptedSubmitted)).isFalse();
    }

    @Test
    @DisplayName("hides a mock test containing an unusable passage, and only that one")
    void hidesOnlyBrokenMockTests() throws SQLException {
        try (Connection c = connection()) {
            assertThat(count(c, "SELECT COUNT(*) FROM mock_tests WHERE mock_test_id = " + brokenMockTest
                    + " AND deleted_at IS NOT NULL")).isEqualTo(1);
            assertThat(count(c, "SELECT COUNT(*) FROM mock_tests WHERE mock_test_id = " + cleanMockTest
                    + " AND deleted_at IS NULL")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("hides the four mock tests V33 built from the placeholders")
    void hidesTheV33MockTests() throws SQLException {
        try (Connection c = connection()) {
            assertThat(count(c, "SELECT COUNT(*) FROM mock_tests WHERE title LIKE 'Cambridge IELTS 19 Test %'"))
                    .as("V33 seeds four").isEqualTo(4);
            assertThat(count(c, "SELECT COUNT(*) FROM mock_tests "
                    + "WHERE title LIKE 'Cambridge IELTS 19 Test %' AND deleted_at IS NULL")).isZero();
        }
    }

    @Test
    @DisplayName("leaves no visible mock test containing an unusable passage")
    void noVisibleMockTestContainsAnUnusablePassage() throws SQLException {
        // The invariant V52 exists to establish, stated directly. Mock tests with no reading
        // passages at all (an older seed has one) are not its business and stay visible.
        try (Connection c = connection()) {
            assertThat(count(c, "SELECT COUNT(*) FROM mock_tests m "
                    + "JOIN mock_test_reading_quizzes mr ON mr.mock_test_id = m.mock_test_id "
                    + "JOIN reading_quizzes q ON q.quiz_id = mr.quiz_id "
                    + "WHERE m.deleted_at IS NULL AND ("
                    + "(q.passage_text LIKE '[Cambridge 19 Test %' AND CHAR_LENGTH(q.passage_text) < 200) "
                    + "OR q.passage_text REGEXP '(-[[:alpha:]]){8}')")).isZero();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long insertQuiz(Connection c, long userId, String passage, boolean submitted) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reading_quizzes (user_id, topic, difficulty, passage_text, is_template, submitted_at) "
                        + "VALUES (?, 'TECHNOLOGY', 'PASSAGE_1', ?, 0, " + (submitted ? "NOW()" : "NULL") + ")",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, passage);
            ps.executeUpdate();
            return generatedKey(ps);
        }
    }

    private static void link(Connection c, long mockTestId, long quizId, int order) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO mock_test_reading_quizzes (mock_test_id, quiz_id, passage_order) VALUES ("
                    + mockTestId + ", " + quizId + ", " + order + ")");
        }
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

    private static long generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean isDeleted(long quizId) throws SQLException {
        try (Connection c = connection()) {
            return count(c, "SELECT COUNT(*) FROM reading_quizzes WHERE quiz_id = " + quizId
                    + " AND deleted_at IS NOT NULL") == 1;
        }
    }
}
