package com.smartprep.service;

import com.smartprep.config.ExamDurationConfig;
import com.smartprep.dto.request.CompleteAttemptRequest;
import com.smartprep.dto.request.StartAttemptRequest;
import com.smartprep.dto.response.AttemptResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ExamAttempt;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SessionStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ExamAttemptRepository;
import com.smartprep.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing exam attempt lifecycle (start → in-progress → completed).
 * Provides server-authoritative timer enforcement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAttemptService {

    private final ExamAttemptRepository attemptRepository;
    private final UserRepository userRepository;
    private final ExamDurationConfig durationConfig;

    /**
     * Start a new exam attempt, or return an existing IN_PROGRESS attempt
     * (handles duplicate-tab and resume-on-reload scenarios).
     */
    @Transactional
    public AttemptResponse startAttempt(Long userId, StartAttemptRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SkillType skillType = SkillType.valueOf(request.getSkillType().toUpperCase());

        // Check for existing IN_PROGRESS attempt for same skill
        Optional<ExamAttempt> existing = attemptRepository
                .findByUserUserIdAndSkillTypeAndStatus(userId, skillType, SessionStatus.IN_PROGRESS);

        if (existing.isPresent()) {
            ExamAttempt attempt = existing.get();
            LocalDateTime now = LocalDateTime.now();

            // If existing attempt has expired, mark it EXPIRED and create new one
            if (now.isAfter(attempt.getDeadline().plusSeconds(ExamDurationConfig.DEADLINE_BUFFER_SECONDS))) {
                attempt.setStatus(SessionStatus.EXPIRED);
                attempt.setAutoSubmitted(true);
                attempt.setSubmittedAt(attempt.getDeadline());
                long spent = Duration.between(attempt.getStartedAt(), attempt.getDeadline()).getSeconds();
                attempt.setTimeSpentSeconds((int) spent);
                attemptRepository.save(attempt);
                log.info("Expired stale attempt {} for user {}", attempt.getAttemptId(), userId);
            } else {
                // Return existing active attempt (resume / dup-tab scenario)
                log.info("Returning existing IN_PROGRESS attempt {} for user {} skill {}",
                        attempt.getAttemptId(), userId, skillType);
                return toResponse(attempt);
            }
        }

        // Create new attempt
        int duration = durationConfig.getEffectiveDuration(skillType, request.getDurationOverride());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusSeconds(duration);

        ExamAttempt attempt = ExamAttempt.builder()
                .user(user)
                .skillType(skillType)
                .durationSeconds(duration)
                .startedAt(now)
                .deadline(deadline)
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .examReferenceIds(request.getExamReferenceIds())
                .build();

        attempt = attemptRepository.save(attempt);
        log.info("Created new attempt {} for user {} skill {} duration {}s deadline {}",
                attempt.getAttemptId(), userId, skillType, duration, deadline);

        return toResponse(attempt);
    }

    /**
     * Get an attempt by ID (for FE to resume countdown after reload).
     */
    @Transactional(readOnly = true)
    public AttemptResponse getAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptRepository.findByAttemptIdAndUserUserId(attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        return toResponse(attempt);
    }

    /**
     * Complete an attempt (mark as SUBMITTED).
     * Idempotent: if already SUBMITTED, returns existing without error.
     */
    @Transactional
    public AttemptResponse completeAttempt(Long attemptId, Long userId, CompleteAttemptRequest request) {
        ExamAttempt attempt = attemptRepository.findByAttemptIdAndUserUserId(attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));

        // Idempotent: already completed
        if (attempt.getStatus() == SessionStatus.SUBMITTED) {
            log.info("Attempt {} already SUBMITTED, returning idempotent response", attemptId);
            return toResponse(attempt);
        }

        LocalDateTime now = LocalDateTime.now();

        // Calculate actual time spent
        long spentSeconds = Duration.between(attempt.getStartedAt(), now).getSeconds();
        // Cap at duration + buffer (don't record more than allowed)
        spentSeconds = Math.min(spentSeconds, attempt.getDurationSeconds() + ExamDurationConfig.DEADLINE_BUFFER_SECONDS);

        attempt.setStatus(SessionStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setTimeSpentSeconds((int) spentSeconds);

        // Auto-submitted flag
        if (request != null && request.getAutoSubmitted() != null) {
            attempt.setAutoSubmitted(request.getAutoSubmitted());
        }

        // Writing per-task time tracking
        if (request != null && request.getTimeSpentTask1() != null) {
            attempt.setTimeSpentTask1(request.getTimeSpentTask1());
        }
        if (request != null && request.getTimeSpentTask2() != null) {
            attempt.setTimeSpentTask2(request.getTimeSpentTask2());
        }

        attempt = attemptRepository.save(attempt);
        log.info("Completed attempt {} for user {}: timeSpent={}s autoSubmitted={}",
                attemptId, userId, spentSeconds, attempt.getAutoSubmitted());

        return toResponse(attempt);
    }

    /**
     * Complete an attempt internally (called by submit services).
     * Returns the completed attempt entity.
     */
    @Transactional
    public ExamAttempt completeAttemptInternal(Long attemptId, Long userId,
                                               boolean autoSubmitted,
                                               Integer timeSpentTask1,
                                               Integer timeSpentTask2) {
        ExamAttempt attempt = attemptRepository.findByAttemptIdAndUserUserId(attemptId, userId)
                .orElse(null);

        if (attempt == null) {
            log.warn("Attempt {} not found for user {}, skipping completion", attemptId, userId);
            return null;
        }

        if (attempt.getStatus() == SessionStatus.SUBMITTED) {
            return attempt; // Idempotent
        }

        LocalDateTime now = LocalDateTime.now();
        long spentSeconds = Duration.between(attempt.getStartedAt(), now).getSeconds();
        spentSeconds = Math.min(spentSeconds, attempt.getDurationSeconds() + ExamDurationConfig.DEADLINE_BUFFER_SECONDS);

        attempt.setStatus(SessionStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setTimeSpentSeconds((int) spentSeconds);
        attempt.setAutoSubmitted(autoSubmitted);

        if (timeSpentTask1 != null) attempt.setTimeSpentTask1(timeSpentTask1);
        if (timeSpentTask2 != null) attempt.setTimeSpentTask2(timeSpentTask2);

        return attemptRepository.save(attempt);
    }

    // ── Mapping ──

    private AttemptResponse toResponse(ExamAttempt attempt) {
        AttemptResponse.AttemptResponseBuilder builder = AttemptResponse.builder()
                .attemptId(attempt.getAttemptId())
                .skillType(attempt.getSkillType().name())
                .durationSeconds(attempt.getDurationSeconds())
                .startedAt(attempt.getStartedAt())
                .deadline(attempt.getDeadline())
                .status(attempt.getStatus().name())
                .autoSubmitted(attempt.getAutoSubmitted())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .timeSpentTask1(attempt.getTimeSpentTask1())
                .timeSpentTask2(attempt.getTimeSpentTask2())
                .examReferenceIds(attempt.getExamReferenceIds());

        // Include suggested durations for Writing
        if (attempt.getSkillType() == SkillType.WRITING) {
            builder.suggestedTask1Duration(ExamDurationConfig.WRITING_TASK1_SUGGESTED);
            builder.suggestedTask2Duration(ExamDurationConfig.WRITING_TASK2_SUGGESTED);
        }

        return builder.build();
    }
}
