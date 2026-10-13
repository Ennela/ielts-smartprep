package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingQuizRepository extends JpaRepository<ReadingQuiz, Long> {

    @Override
    @Query("SELECT q FROM ReadingQuiz q WHERE q.quizId = :id AND q.deletedAt IS NULL")
    Optional<ReadingQuiz> findById(@Param("id") Long id);

    @Query("SELECT q FROM ReadingQuiz q WHERE q.quizId = :id")
    Optional<ReadingQuiz> findIncludingDeletedById(@Param("id") Long id);

    @Override
    @Query("SELECT q FROM ReadingQuiz q WHERE q.deletedAt IS NULL")
    Page<ReadingQuiz> findAll(Pageable pageable);

    @Override
    @Query("SELECT q FROM ReadingQuiz q WHERE q.deletedAt IS NULL")
    List<ReadingQuiz> findAll();

    @Query("SELECT q FROM ReadingQuiz q WHERE q.user.userId = :userId " +
           "AND q.deletedAt IS NULL ORDER BY q.createdAt DESC")
    List<ReadingQuiz> findByUserUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /** The user's submitted quizzes, one page at a time; order comes from the Pageable. */
    @Query("SELECT q FROM ReadingQuiz q WHERE q.user.userId = :userId " +
           "AND q.submittedAt IS NOT NULL AND q.deletedAt IS NULL")
    Page<ReadingQuiz> findSubmittedByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT q FROM ReadingQuiz q WHERE q.quizId = :quizId " +
           "AND q.user.userId = :userId AND q.deletedAt IS NULL")
    Optional<ReadingQuiz> findByQuizIdAndUserUserId(
            @Param("quizId") Long quizId,
            @Param("userId") Long userId);

    @Query("SELECT q FROM ReadingQuiz q WHERE " +
           "((:source = 'ADMIN' AND q.isTemplate = true) OR " +
           " (:source = 'AI' AND q.isTemplate = false AND q.parentTemplateId IS NULL) OR " +
           " (:source IS NULL AND (q.isTemplate = true OR (q.isTemplate = false AND q.parentTemplateId IS NULL)))) " +
           "AND (:topic IS NULL OR q.topic = :topic) " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) " +
           "AND q.deletedAt IS NULL")
    Page<ReadingQuiz> findQuizzesForAdmin(
            @Param("topic") Topic topic,
            @Param("difficulty") Difficulty difficulty,
            @Param("source") String source,
            Pageable pageable);

    // Admin "Archived" view: only soft-deleted rows, newest archive first.
    @Query("SELECT q FROM ReadingQuiz q WHERE q.deletedAt IS NOT NULL " +
           "AND (:topic IS NULL OR q.topic = :topic) " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) ORDER BY q.deletedAt DESC")
    Page<ReadingQuiz> findArchivedForAdmin(
            @Param("topic") Topic topic,
            @Param("difficulty") Difficulty difficulty,
            Pageable pageable);

    @Query("SELECT q FROM ReadingQuiz q WHERE q.contentStatus = :contentStatus " +
           "AND q.deletedAt IS NULL")
    Page<ReadingQuiz> findByContentStatus(
            @Param("contentStatus") ContentStatus contentStatus,
            Pageable pageable);

    /**
     * Mark the caller's quizzes submitted, but only those not submitted yet.
     *
     * <p>Returns how many rows it changed. Grading reads {@code submittedAt} and then writes
     * it, and two concurrent submits both read it as null; a check alone cannot stop both
     * from grading. This single conditional UPDATE can: InnoDB makes the second one wait for
     * the first to commit, re-evaluates the condition, and finds nothing left to claim. A
     * count lower than the number of ids asked for means the caller lost that race.
     */
    @Modifying
    @Query("UPDATE ReadingQuiz q SET q.submittedAt = :now "
            + "WHERE q.quizId IN :quizIds AND q.user.userId = :userId AND q.submittedAt IS NULL")
    int claimForSubmission(@Param("quizIds") Collection<Long> quizIds,
                           @Param("userId") Long userId,
                           @Param("now") LocalDateTime now);

    // ── Learner-facing ──────────────────────────────────────────────────────
    //
    // Everything a learner is offered from a shared pool comes through the methods below,
    // and they return reviewed (PUBLISHED) content only. The queries above do not filter on
    // content_status, and until now every learner list used them: a DRAFT written by an
    // admin, or an unreviewed AI item another learner had generated, was handed to anyone.
    // Admin screens keep using the unfiltered queries, because drafts are their job.
    // Fetching one item by id is left alone, so a learner can still open the item they
    // have just generated.

    /** {@link #findQuizzesForAdmin} restricted to reviewed content. */
    @Query("SELECT q FROM ReadingQuiz q WHERE " +
           "((:source = 'ADMIN' AND q.isTemplate = true) OR " +
           " (:source = 'AI' AND q.isTemplate = false AND q.parentTemplateId IS NULL) OR " +
           " (:source IS NULL AND (q.isTemplate = true OR (q.isTemplate = false AND q.parentTemplateId IS NULL)))) " +
           "AND (:topic IS NULL OR q.topic = :topic) " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty) " +
           "AND q.deletedAt IS NULL AND q.contentStatus = com.smartprep.model.enums.ContentStatus.PUBLISHED")
    Page<ReadingQuiz> findPublishedQuizzes(
            @Param("topic") Topic topic,
            @Param("difficulty") Difficulty difficulty,
            @Param("source") String source,
            Pageable pageable);

    /** Published admin templates for this topic and difficulty the user has never been given a copy of. */
    @Query("SELECT q FROM ReadingQuiz q WHERE q.isTemplate = true " +
           "AND q.topic = :topic AND q.difficulty = :difficulty " +
           "AND q.deletedAt IS NULL AND q.contentStatus = com.smartprep.model.enums.ContentStatus.PUBLISHED " +
           "AND NOT EXISTS (SELECT c FROM ReadingQuiz c WHERE c.parentTemplateId = q.quizId AND c.user.userId = :userId)")
    List<ReadingQuiz> findUnseenPublishedTemplates(
            @Param("topic") Topic topic,
            @Param("difficulty") Difficulty difficulty,
            @Param("userId") Long userId,
            Pageable pageable);
}
