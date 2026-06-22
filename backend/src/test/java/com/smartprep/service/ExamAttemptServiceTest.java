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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamAttemptServiceTest {

    @Mock
    private ExamAttemptRepository attemptRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private ExamDurationConfig durationConfig = new ExamDurationConfig();

    @InjectMocks
    private ExamAttemptService examAttemptService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).username("teststudent").build();
    }

    // ── startAttempt tests ──

    @Test
    @DisplayName("should create new WRITING attempt with correct deadline (startedAt + 3600s)")
    void startAttempt_writing_newAttempt() {
        StartAttemptRequest request = new StartAttemptRequest();
        request.setSkillType("WRITING");
        request.setExamReferenceIds("[42]");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserUserIdAndSkillTypeAndStatus(
                1L, SkillType.WRITING, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> {
            ExamAttempt a = inv.getArgument(0);
            a.setAttemptId(100L);
            return a;
        });

        AttemptResponse response = examAttemptService.startAttempt(1L, request);

        assertNotNull(response);
        assertEquals(100L, response.getAttemptId());
        assertEquals("WRITING", response.getSkillType());
        assertEquals(3600, response.getDurationSeconds());
        assertNotNull(response.getDeadline());
        assertEquals("IN_PROGRESS", response.getStatus());

        // Verify deadline = startedAt + 3600 seconds
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository).save(captor.capture());
        ExamAttempt saved = captor.getValue();
        assertEquals(saved.getStartedAt().plusSeconds(3600), saved.getDeadline());
    }

    @Test
    @DisplayName("should return suggested durations for WRITING skill")
    void startAttempt_writing_suggestedDurations() {
        StartAttemptRequest request = new StartAttemptRequest();
        request.setSkillType("WRITING");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserUserIdAndSkillTypeAndStatus(
                1L, SkillType.WRITING, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> {
            ExamAttempt a = inv.getArgument(0);
            a.setAttemptId(101L);
            return a;
        });

        AttemptResponse response = examAttemptService.startAttempt(1L, request);

        assertEquals(1200, response.getSuggestedTask1Duration()); // 20 min
        assertEquals(2400, response.getSuggestedTask2Duration()); // 40 min
    }

    @Test
    @DisplayName("should resume existing IN_PROGRESS attempt instead of creating new one")
    void startAttempt_resumeExisting() {
        StartAttemptRequest request = new StartAttemptRequest();
        request.setSkillType("WRITING");

        LocalDateTime now = LocalDateTime.now();
        ExamAttempt existing = ExamAttempt.builder()
                .attemptId(50L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(now.minusMinutes(10))
                .deadline(now.plusMinutes(50))
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserUserIdAndSkillTypeAndStatus(
                1L, SkillType.WRITING, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(existing));

        AttemptResponse response = examAttemptService.startAttempt(1L, request);

        assertEquals(50L, response.getAttemptId());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("should expire stale attempt and create new one")
    void startAttempt_expireStaleAndCreateNew() {
        StartAttemptRequest request = new StartAttemptRequest();
        request.setSkillType("WRITING");

        LocalDateTime now = LocalDateTime.now();
        ExamAttempt stale = ExamAttempt.builder()
                .attemptId(60L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(now.minusHours(2))
                .deadline(now.minusHours(1)) // expired
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserUserIdAndSkillTypeAndStatus(
                1L, SkillType.WRITING, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(stale));
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> {
            ExamAttempt a = inv.getArgument(0);
            if (a.getAttemptId() == null || a.getAttemptId() != 60L) {
                a.setAttemptId(70L); // New attempt
            }
            return a;
        });

        AttemptResponse response = examAttemptService.startAttempt(1L, request);

        // Should have saved twice: once to expire stale, once to create new
        verify(attemptRepository, times(2)).save(any(ExamAttempt.class));
        // Stale should be marked EXPIRED
        assertEquals(SessionStatus.EXPIRED, stale.getStatus());
        assertTrue(stale.getAutoSubmitted());
    }

    @Test
    @DisplayName("should use custom durationOverride when provided")
    void startAttempt_customDuration() {
        StartAttemptRequest request = new StartAttemptRequest();
        request.setSkillType("WRITING");
        request.setDurationOverride(1800); // 30 min

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserUserIdAndSkillTypeAndStatus(
                1L, SkillType.WRITING, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> {
            ExamAttempt a = inv.getArgument(0);
            a.setAttemptId(80L);
            return a;
        });

        AttemptResponse response = examAttemptService.startAttempt(1L, request);

        assertEquals(1800, response.getDurationSeconds());
    }

    // ── completeAttempt tests ──

    @Test
    @DisplayName("should mark attempt as SUBMITTED with correct time spent")
    void completeAttempt_success() {
        LocalDateTime started = LocalDateTime.now().minusMinutes(45);
        ExamAttempt attempt = ExamAttempt.builder()
                .attemptId(100L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(started)
                .deadline(started.plusSeconds(3600))
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        CompleteAttemptRequest request = new CompleteAttemptRequest();
        request.setAutoSubmitted(false);
        request.setTimeSpentTask1(600);
        request.setTimeSpentTask2(2100);

        when(attemptRepository.findByAttemptIdAndUserUserId(100L, 1L))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        AttemptResponse response = examAttemptService.completeAttempt(100L, 1L, request);

        assertEquals("SUBMITTED", response.getStatus());
        assertFalse(response.getAutoSubmitted());
        assertNotNull(response.getTimeSpentSeconds());
        assertEquals(600, response.getTimeSpentTask1());
        assertEquals(2100, response.getTimeSpentTask2());
    }

    @Test
    @DisplayName("should handle auto-submitted attempt correctly")
    void completeAttempt_autoSubmitted() {
        LocalDateTime started = LocalDateTime.now().minusMinutes(60);
        ExamAttempt attempt = ExamAttempt.builder()
                .attemptId(110L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(started)
                .deadline(started.plusSeconds(3600))
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        CompleteAttemptRequest request = new CompleteAttemptRequest();
        request.setAutoSubmitted(true);

        when(attemptRepository.findByAttemptIdAndUserUserId(110L, 1L))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        AttemptResponse response = examAttemptService.completeAttempt(110L, 1L, request);

        assertTrue(response.getAutoSubmitted());
    }

    @Test
    @DisplayName("completeAttempt should be idempotent — return existing if already SUBMITTED")
    void completeAttempt_idempotent() {
        ExamAttempt attempt = ExamAttempt.builder()
                .attemptId(120L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(LocalDateTime.now().minusMinutes(30))
                .deadline(LocalDateTime.now().plusMinutes(30))
                .status(SessionStatus.SUBMITTED)
                .autoSubmitted(false)
                .timeSpentSeconds(1800)
                .build();

        when(attemptRepository.findByAttemptIdAndUserUserId(120L, 1L))
                .thenReturn(Optional.of(attempt));

        AttemptResponse response = examAttemptService.completeAttempt(120L, 1L, null);

        assertEquals("SUBMITTED", response.getStatus());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("completeAttempt should cap time spent at duration + buffer")
    void completeAttempt_capsTimeSpent() {
        // Started 2 hours ago, duration 1 hour — way overdue
        LocalDateTime started = LocalDateTime.now().minusHours(2);
        ExamAttempt attempt = ExamAttempt.builder()
                .attemptId(130L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(started)
                .deadline(started.plusSeconds(3600))
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        when(attemptRepository.findByAttemptIdAndUserUserId(130L, 1L))
                .thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any(ExamAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        AttemptResponse response = examAttemptService.completeAttempt(130L, 1L, null);

        // Time spent should be capped at 3600 + 10 (buffer)
        assertTrue(response.getTimeSpentSeconds() <= 3600 + ExamDurationConfig.DEADLINE_BUFFER_SECONDS);
    }

    // ── getAttempt tests ──

    @Test
    @DisplayName("getAttempt should return attempt for valid user")
    void getAttempt_success() {
        ExamAttempt attempt = ExamAttempt.builder()
                .attemptId(140L)
                .user(user)
                .skillType(SkillType.WRITING)
                .durationSeconds(3600)
                .startedAt(LocalDateTime.now().minusMinutes(10))
                .deadline(LocalDateTime.now().plusMinutes(50))
                .status(SessionStatus.IN_PROGRESS)
                .autoSubmitted(false)
                .build();

        when(attemptRepository.findByAttemptIdAndUserUserId(140L, 1L))
                .thenReturn(Optional.of(attempt));

        AttemptResponse response = examAttemptService.getAttempt(140L, 1L);

        assertEquals(140L, response.getAttemptId());
        assertEquals("IN_PROGRESS", response.getStatus());
    }

    @Test
    @DisplayName("getAttempt should throw when attempt not found")
    void getAttempt_notFound() {
        when(attemptRepository.findByAttemptIdAndUserUserId(999L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> examAttemptService.getAttempt(999L, 1L));
    }

    // ── ExamDurationConfig tests ──

    @Test
    @DisplayName("ExamDurationConfig returns correct defaults for each skill")
    void durationConfig_defaults() {
        assertEquals(3600, durationConfig.getDefaultDuration(SkillType.WRITING));
        assertEquals(3600, durationConfig.getDefaultDuration(SkillType.READING));
        assertEquals(1920, durationConfig.getDefaultDuration(SkillType.LISTENING));
    }

    @Test
    @DisplayName("ExamDurationConfig uses override when provided")
    void durationConfig_override() {
        assertEquals(1800, durationConfig.getEffectiveDuration(SkillType.WRITING, 1800));
    }

    @Test
    @DisplayName("ExamDurationConfig uses default when override is null")
    void durationConfig_nullOverride() {
        assertEquals(3600, durationConfig.getEffectiveDuration(SkillType.WRITING, null));
    }
}
