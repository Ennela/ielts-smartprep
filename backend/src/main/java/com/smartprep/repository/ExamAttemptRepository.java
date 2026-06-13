package com.smartprep.repository;

import com.smartprep.model.entity.ExamAttempt;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findByAttemptIdAndUserUserId(Long attemptId, Long userId);

    /**
     * Find an active (IN_PROGRESS) attempt for a user + skill.
     * Used for duplicate-tab prevention and resume-on-reload.
     */
    Optional<ExamAttempt> findByUserUserIdAndSkillTypeAndStatus(
            Long userId, SkillType skillType, SessionStatus status);

    /**
     * Listing completed attempts (for potential future use in history).
     */
    List<ExamAttempt> findByUserUserIdAndSkillTypeAndStatusOrderByStartedAtDesc(
            Long userId, SkillType skillType, SessionStatus status);
}
