package com.smartprep.repository;

import com.smartprep.model.entity.ReadingQuiz;
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

    List<ReadingQuiz> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ReadingQuiz> findByQuizIdAndUserUserId(Long quizId, Long userId);

    @Query("SELECT q FROM ReadingQuiz q WHERE " +
           "((:source = 'ADMIN' AND q.isTemplate = true) OR " +
           " (:source = 'AI' AND q.isTemplate = false AND q.parentTemplateId IS NULL) OR " +
           " (:source IS NULL AND (q.isTemplate = true OR (q.isTemplate = false AND q.parentTemplateId IS NULL)))) " +
           "AND (:topic IS NULL OR q.topic = :topic) " +
           "AND (:difficulty IS NULL OR q.difficulty = :difficulty)")
    Page<ReadingQuiz> findQuizzesForAdmin(
            @Param("topic") Topic topic,
            @Param("difficulty") Difficulty difficulty,
            @Param("source") String source,
            Pageable pageable);
}
