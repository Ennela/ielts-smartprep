package com.smartprep.repository;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Makes seeded Cambridge 19 content visible for the length of one test.
 *
 * <p>Several integration tests use the seed for its <em>shape</em>: a paper with four
 * listening parts and three reading passages, or a catalogue of reading templates with
 * question counts. They exercise grading, score history, analytics and paging. The content
 * itself was never real -- the reading passages are title-only placeholders -- which is why
 * V52 hides it from learners.
 *
 * <p>Call these inside a {@code @Transactional} test. The update runs on the test's own
 * connection, so the rest of the test sees the rows, and the rollback at the end hides them
 * again: no other test, and no learner, ever sees them restored.
 */
public final class SeededContentFixture {

    public static final String PAPER_TITLE = "Cambridge IELTS 19 Test 1";

    private SeededContentFixture() {
    }

    /** The V33 paper, with its four listening parts and three reading passages. */
    public static void restorePaper(JdbcTemplate jdbcTemplate) {
        int restored = jdbcTemplate.update("UPDATE mock_tests SET deleted_at = NULL WHERE title = ?", PAPER_TITLE);
        if (restored != 1) {
            throw new IllegalStateException("Expected exactly one seeded paper titled '" + PAPER_TITLE
                    + "' but restored " + restored + "; the V33 seed has changed");
        }
    }

    /** The twelve V32 reading templates. */
    public static void restoreReadingTemplates(JdbcTemplate jdbcTemplate) {
        int restored = jdbcTemplate.update("UPDATE reading_quizzes SET deleted_at = NULL "
                + "WHERE is_template = TRUE AND passage_text LIKE '[Cambridge 19 Test %'");
        if (restored == 0) {
            throw new IllegalStateException("No seeded reading templates to restore; the V32 seed has changed");
        }
    }
}
