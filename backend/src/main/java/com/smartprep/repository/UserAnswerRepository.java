package com.smartprep.repository;

import com.smartprep.model.entity.UserAnswer;
import com.smartprep.model.enums.SkillType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAnswerRepository extends JpaRepository<UserAnswer, Long> {

    List<UserAnswer> findByScoreHistoryHistoryIdOrderByQuestionNoAsc(Long historyId);

    /**
     * Correct and total answers per question type across everything a user has sat.
     *
     * <p>One aggregate query, returning {@code [questionType, correctCount, totalCount]}
     * rows. This replaces loading every score-history row for the user and then lazily
     * walking each one's answers -- a SELECT per sitting, unbounded, on every dashboard
     * open (AUDIT P1).
     */
    @Query("SELECT a.questionType, SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END), COUNT(a) " +
           "FROM UserAnswer a WHERE a.scoreHistory.user.userId = :userId " +
           "GROUP BY a.questionType")
    List<Object[]> accuracyByQuestionType(@Param("userId") Long userId);

    /** Same as {@link #accuracyByQuestionType(Long)}, restricted to one skill. */
    @Query("SELECT a.questionType, SUM(CASE WHEN a.isCorrect = true THEN 1 ELSE 0 END), COUNT(a) " +
           "FROM UserAnswer a WHERE a.scoreHistory.user.userId = :userId " +
           "AND a.scoreHistory.skillType = :skill " +
           "GROUP BY a.questionType")
    List<Object[]> accuracyByQuestionType(@Param("userId") Long userId, @Param("skill") SkillType skill);
}
