package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.SpeakingSubmissionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.SpeakingPrompt;
import com.smartprep.model.entity.SpeakingSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.SpeakingPromptRepository;
import com.smartprep.repository.SpeakingSubmissionRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.ai.SpeakingGradingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpeakingServiceTest {

    @Mock private SpeakingPromptRepository promptRepository;
    @Mock private SpeakingSubmissionRepository submissionRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private SpeakingGradingService gradingService;
    @Mock private StorageService storageService;
    @Mock private StatsService statsService;
    @Mock private TransactionTemplate transactionTemplate;

    private SpeakingService service;
    private SpeakingPrompt part2;
    private final byte[] audio = new byte[2048];

    @BeforeEach
    void setUp() {
        service = new SpeakingService(promptRepository, submissionRepository, scoreHistoryRepository, userRepository,
                gradingService, storageService, statsService, transactionTemplate, new ObjectMapper());
        part2 = SpeakingPrompt.builder().promptId(4L).part(2).topic("A journey")
                .questionText("Describe a journey.").cuePoints("where\nwho").build();
    }

    private SpeakingGradingService.Result result() {
        return SpeakingGradingService.Result.builder()
                .transcript("I went to Da Lat.")
                .fluencyBand(new BigDecimal("6.0")).lexicalBand(new BigDecimal("6.5"))
                .grammarBand(new BigDecimal("6.0")).pronunciationBand(new BigDecimal("7.0"))
                .overallBand(new BigDecimal("6.5"))
                .summary("Good.").strengths(List.of("Range")).improvements(List.of("Linking"))
                .criteriaComments(Map.of("pronunciation", "Clear"))
                .build();
    }

    @Test
    @DisplayName("grades, stores the recording, and records a SPEAKING score")
    @SuppressWarnings("unchecked")
    void gradesAndRecords() {
        when(promptRepository.findById(4L)).thenReturn(Optional.of(part2));
        when(gradingService.grade(part2, audio, "audio/webm", 95)).thenReturn(result());
        when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        when(userRepository.findById(9L)).thenReturn(Optional.of(User.builder().userId(9L).build()));
        when(submissionRepository.save(any())).thenAnswer(inv -> {
            SpeakingSubmission s = inv.getArgument(0);
            s.setSubmissionId(31L);
            s.setSubmittedAt(LocalDateTime.of(2026, 10, 5, 9, 0));
            return s;
        });

        SpeakingSubmissionResponse response = service.grade(9L, 4L, audio, "audio/webm;codecs=opus", 95);

        assertEquals(31L, response.getSubmissionId());
        assertEquals(new BigDecimal("6.5"), response.getOverallBand());
        assertEquals(List.of("Linking"), response.getImprovements());
        assertEquals(120, response.getPrompt().getMaxSpeakSeconds());
        assertEquals(60, response.getPrompt().getPrepSeconds());
        verify(storageService).uploadRecording(startsWith("speaking_9_"), eq(audio), eq("audio/webm"));

        ArgumentCaptor<ScoreHistory> history = ArgumentCaptor.forClass(ScoreHistory.class);
        verify(scoreHistoryRepository).save(history.capture());
        assertEquals(SkillType.SPEAKING, history.getValue().getSkillType());
        assertEquals(new BigDecimal("6.5"), history.getValue().getScore());
        assertEquals("PART_2", history.getValue().getDifficulty());
        verify(statsService).evictOverviewCache(9L);
    }

    @Test
    @DisplayName("refuses bad recordings before spending a Gemini call")
    void validates() {
        when(promptRepository.findById(4L)).thenReturn(Optional.of(part2));

        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, audio, "video/mp4", 60));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, audio, "audio/webm", 2));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, audio, "audio/webm", 200));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, new byte[0], "audio/webm", 60));
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 4L, new byte[SpeakingService.MAX_BYTES + 1], "audio/webm", 60));
        verifyNoInteractions(gradingService, storageService);
    }

    @Test
    @DisplayName("hides another user's result and recording")
    void ownerOnly() {
        when(submissionRepository.findBySubmissionIdAndUserUserId(31L, 9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSubmission(9L, 31L));
        assertThrows(ResourceNotFoundException.class, () -> service.recording(9L, 31L));
        verifyNoInteractions(storageService);
    }
}
