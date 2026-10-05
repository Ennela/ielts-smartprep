package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.SpeakingSubmissionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ScoreHistory;
import com.smartprep.model.entity.SpeakingAnswer;
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
    private SpeakingPrompt part1;
    private final byte[] audio = new byte[2048];

    private static SpeakingService.Upload upload(byte[] audio, String type, int seconds) {
        return new SpeakingService.Upload(audio, type, seconds);
    }

    @BeforeEach
    void setUp() {
        service = new SpeakingService(promptRepository, submissionRepository, scoreHistoryRepository, userRepository,
                gradingService, storageService, statsService, transactionTemplate, new ObjectMapper());
        part2 = SpeakingPrompt.builder().promptId(4L).part(2).topic("A journey")
                .questionText("Describe a journey.").cuePoints("where\nwho").build();
        part1 = SpeakingPrompt.builder().promptId(1L).part(1).topic("Hometown")
                .questionText("Where is your hometown?\nDo you like it?").build();
    }

    private void persistInline() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        when(userRepository.findById(9L)).thenReturn(Optional.of(User.builder().userId(9L).build()));
        when(submissionRepository.save(any())).thenAnswer(inv -> {
            SpeakingSubmission s = inv.getArgument(0);
            s.setSubmissionId(31L);
            s.setSubmittedAt(LocalDateTime.of(2026, 10, 5, 9, 0));
            return s;
        });
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
    @DisplayName("grades a Part 2 answer, stores the recording, and records a SPEAKING score")
    void gradesAndRecords() {
        when(promptRepository.findById(4L)).thenReturn(Optional.of(part2));
        when(gradingService.grade(eq(part2), eq(List.of("Describe a journey.")), anyList())).thenReturn(result());
        persistInline();

        SpeakingSubmissionResponse response = service.grade(9L, 4L, List.of(upload(audio, "audio/webm;codecs=opus", 95)));

        assertEquals(31L, response.getSubmissionId());
        assertEquals(new BigDecimal("6.5"), response.getOverallBand());
        assertEquals(List.of("Linking"), response.getImprovements());
        assertEquals(120, response.getPrompt().getMaxSpeakSeconds());
        assertEquals(60, response.getPrompt().getPrepSeconds());
        verify(storageService).uploadRecording(startsWith("speaking_9_"), eq(audio), eq("audio/webm"));
        assertEquals("I went to Da Lat.", response.getTranscript());
        assertTrue(response.getAnswers().isEmpty());

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
        when(promptRepository.findById(1L)).thenReturn(Optional.of(part1));

        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, List.of(upload(audio, "video/mp4", 60))));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, List.of(upload(audio, "audio/webm", 2))));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, List.of(upload(audio, "audio/webm", 200))));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 4L, List.of(upload(new byte[0], "audio/webm", 60))));
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 4L, List.of(upload(new byte[SpeakingService.MAX_BYTES + 1], "audio/webm", 60))));
        // Part 2 is one take; Part 1/3 needs one answer per question, each long enough and under the limit.
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 4L, List.of(upload(audio, "audio/webm", 60), upload(audio, "audio/webm", 60))));
        assertThrows(IllegalArgumentException.class, () -> service.grade(9L, 1L, List.of(upload(audio, "audio/webm", 20))));
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 1L, List.of(upload(audio, "audio/webm", 20), upload(audio, "audio/webm", 2))));
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 1L, List.of(upload(audio, "audio/webm", 20), upload(audio, "audio/webm", 60))));
        byte[] half = new byte[SpeakingService.MAX_BYTES / 2 + 1];
        assertThrows(IllegalArgumentException.class,
                () -> service.grade(9L, 1L, List.of(upload(half, "audio/webm", 20), upload(half, "audio/webm", 20))));
        verifyNoInteractions(gradingService, storageService);
    }

    @Test
    @DisplayName("grades Part 1 answers together and keeps each recording, transcript and comment")
    @SuppressWarnings("unchecked")
    void gradesEachQuestion() {
        byte[] first = new byte[100];
        byte[] second = new byte[200];
        when(promptRepository.findById(1L)).thenReturn(Optional.of(part1));
        SpeakingGradingService.Result graded = result();
        graded.setTranscript("");
        graded.setAnswers(List.of(
                new SpeakingGradingService.AnswerFeedback("I come from Hue.", "Add a reason."),
                new SpeakingGradingService.AnswerFeedback("Yes, I love it.", "Good.")));
        when(gradingService.grade(eq(part1), eq(List.of("Where is your hometown?", "Do you like it?")), anyList()))
                .thenReturn(graded);
        persistInline();

        SpeakingSubmissionResponse response = service.grade(9L, 1L,
                List.of(upload(first, "audio/webm", 22), upload(second, "audio/ogg", 3)));

        ArgumentCaptor<List<SpeakingGradingService.Recording>> sent = ArgumentCaptor.forClass(List.class);
        verify(gradingService).grade(eq(part1), anyList(), sent.capture());
        assertEquals(2, sent.getValue().size());
        assertEquals("audio/ogg", sent.getValue().get(1).mimeType());
        verify(storageService).uploadRecording(argThat(k -> k.startsWith("speaking_9_") && k.endsWith("_q1.webm")), eq(first), eq("audio/webm"));
        verify(storageService).uploadRecording(argThat(k -> k.endsWith("_q2.ogg")), eq(second), eq("audio/ogg"));

        ArgumentCaptor<SpeakingSubmission> saved = ArgumentCaptor.forClass(SpeakingSubmission.class);
        verify(submissionRepository).save(saved.capture());
        assertNull(saved.getValue().getAudioKey());
        assertEquals(25, saved.getValue().getDurationSeconds());
        assertEquals(2, saved.getValue().getAnswers().size());
        assertSame(saved.getValue(), saved.getValue().getAnswers().get(0).getSubmission());

        assertNull(response.getTranscript());
        assertEquals(40, response.getPrompt().getMaxSpeakSeconds());
        assertEquals(2, response.getAnswers().size());
        assertEquals("Do you like it?", response.getAnswers().get(1).getQuestion());
        assertEquals("Yes, I love it.", response.getAnswers().get(1).getTranscript());
        assertEquals("Add a reason.", response.getAnswers().get(0).getComment());
        assertEquals(3, response.getAnswers().get(1).getDurationSeconds());

        ArgumentCaptor<ScoreHistory> history = ArgumentCaptor.forClass(ScoreHistory.class);
        verify(scoreHistoryRepository).save(history.capture());
        assertEquals(25, history.getValue().getTimeSpentSeconds());
        assertEquals("PART_1", history.getValue().getDifficulty());
    }

    @Test
    @DisplayName("plays each Part 1/3 answer, and has no single recording for such a submission")
    void answerRecordings() {
        SpeakingSubmission submission = SpeakingSubmission.builder().submissionId(31L).prompt(part1).build();
        submission.getAnswers().add(SpeakingAnswer.builder().submission(submission).questionIndex(1)
                .audioKey("k_q2.webm").audioMimeType("audio/webm").durationSeconds(12).build());
        when(submissionRepository.findBySubmissionIdAndUserUserId(31L, 9L)).thenReturn(Optional.of(submission));
        when(storageService.downloadAudio("k_q2.webm")).thenReturn(audio);

        assertEquals(Map.entry(audio, "audio/webm"), service.answerRecording(9L, 31L, 1));
        assertThrows(ResourceNotFoundException.class, () -> service.answerRecording(9L, 31L, 0));
        assertThrows(ResourceNotFoundException.class, () -> service.recording(9L, 31L));
    }

    @Test
    @DisplayName("hides another user's result and recording")
    void ownerOnly() {
        when(submissionRepository.findBySubmissionIdAndUserUserId(31L, 9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSubmission(9L, 31L));
        assertThrows(ResourceNotFoundException.class, () -> service.recording(9L, 31L));
        assertThrows(ResourceNotFoundException.class, () -> service.answerRecording(9L, 31L, 0));
        verifyNoInteractions(storageService);
    }
}
