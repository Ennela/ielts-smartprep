package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Query("SELECT q FROM ReadingQuiz q WHERE q.contentStatus = :contentStatus " +
           "AND q.deletedAt IS NULL")
    Page<ReadingQuiz> findByContentStatus(
            @Param("contentStatus") ContentStatus contentStatus,
            Pageable pageable);
}
