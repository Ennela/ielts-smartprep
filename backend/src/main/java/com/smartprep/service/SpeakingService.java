package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.SpeakingAnswerResponse;
import com.smartprep.dto.response.SpeakingHistoryItemResponse;
import com.smartprep.dto.response.SpeakingPromptResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Speaking practice: the prompts, grading a recorded answer, and the learner's results.
 *
 * Part 2 is answered in one recording. Part 1 and Part 3 are answered one question at a
 * time, as in the test: one recording per question, graded together.
 *
 * Grading calls Gemini, which can take tens of seconds, so it runs outside any transaction
 * (no database connection is held while waiting); the recordings, the submission and its
 * score_history row are then written together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingService {

    /** Formats MediaRecorder produces in current browsers, plus common uploads. */
    static final Set<String> ACCEPTED_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/aac");
    /** For all the recordings of one submission together. */
    static final int MAX_BYTES = 5 * 1024 * 1024;
    /** Shortest Part 2 answer worth grading. */
    static final int MIN_SECONDS = 5;
    /** Shortest Part 1/3 answer: anything less is a slip of the button, not an answer. */
    static final int MIN_ANSWER_SECONDS = 3;

    private final SpeakingPromptRepository promptRepository;
    private final SpeakingSubmissionRepository submissionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final SpeakingGradingService gradingService;
    private final StorageService storageService;
    private final StatsService statsService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** One uploaded recording, before validation. */
    public record Upload(byte[] audio, String contentType, int durationSeconds) {}

    /** Part 2 has a minute to prepare and two to speak; Parts 1 and 3 are answered at once. */
    static int prepSeconds(int part) {
        return part == 2 ? 60 : 0;
    }

    /** The Part 2 answer; for Parts 1 and 3, each answer (short answers, longer discussion). */
    static int maxSpeakSeconds(int part) {
        return switch (part) {
            case 1 -> 40;
            case 2 -> 120;
            default -> 75;
        };
    }

    public List<SpeakingPromptResponse> listPrompts(Integer part) {
        List<SpeakingPrompt> prompts = part == null
                ? promptRepository.findAllByOrderByPartAscPromptIdAsc()
                : promptRepository.findByPartOrderByPromptIdAsc(part);
        return prompts.stream().map(SpeakingService::toPromptResponse).toList();
    }

    /**
     * @param uploads Part 2: the one answer; Part 1/3: one per question of the prompt, in order
     */
    public SpeakingSubmissionResponse grade(Long userId, Long promptId, List<Upload> uploads) {
        SpeakingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking prompt not found: " + promptId));
        boolean perQuestion = prompt.getPart() != 2;
        List<String> questions = lines(prompt.getQuestionText());
        int expected = perQuestion ? questions.size() : 1;
        if (uploads.size() != expected) {
            throw new IllegalArgumentException(perQuestion
                    ? "Answer each of the " + expected + " questions (got " + uploads.size() + " recordings)"
                    : "A Part 2 answer is one recording");
        }
        int minSeconds = perQuestion ? MIN_ANSWER_SECONDS : MIN_SECONDS;
        int maxSeconds = maxSpeakSeconds(prompt.getPart());
        long totalBytes = 0;
        List<SpeakingGradingService.Recording> recordings = new ArrayList<>();
        for (int i = 0; i < uploads.size(); i++) {
            Upload upload = uploads.get(i);
            String which = perQuestion ? "The answer to question " + (i + 1) : "The recording";
            String mimeType = normaliseType(upload.contentType());
            if (upload.audio() == null || upload.audio().length == 0) {
                throw new IllegalArgumentException(which + " is empty");
            }
            totalBytes += upload.audio().length;
            if (upload.durationSeconds() < minSeconds) {
                throw new IllegalArgumentException(which + " is too short to grade (at least " + minSeconds + " seconds)");
            }
            // A little slack: the browser stops the recorder at the limit, but its clock and ours differ.
            if (upload.durationSeconds() > maxSeconds + 10) {
                throw new IllegalArgumentException("Part " + prompt.getPart() + " answers are at most " + maxSeconds + " seconds");
            }
            recordings.add(new SpeakingGradingService.Recording(upload.audio(), mimeType, upload.durationSeconds()));
        }
        if (totalBytes > MAX_BYTES) {
            throw new IllegalArgumentException("The recordings are larger than 5 MB");
        }
        int totalSeconds = recordings.stream().mapToInt(SpeakingGradingService.Recording::durationSeconds).sum();

        SpeakingGradingService.Result result = gradingService.grade(prompt, questions, recordings);

        String keyBase = "speaking_" + userId + "_" + System.currentTimeMillis();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < recordings.size(); i++) {
            SpeakingGradingService.Recording r = recordings.get(i);
            String key = keyBase + (perQuestion ? "_q" + (i + 1) : "") + extension(r.mimeType());
            storageService.uploadRecording(key, r.audio(), r.mimeType());
            keys.add(key);
        }

        SpeakingSubmission saved = transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            SpeakingSubmission submission = SpeakingSubmission.builder()
                    .user(user).prompt(prompt)
                    .durationSeconds(totalSeconds)
                    .transcript(perQuestion ? null : result.getTranscript())
                    .overallBand(result.getOverallBand())
                    .fluencyBand(result.getFluencyBand()).lexicalBand(result.getLexicalBand())
                    .grammarBand(result.getGrammarBand()).pronunciationBand(result.getPronunciationBand())
                    .feedbackJson(feedbackJson(result))
                    .build();
            if (perQuestion) {
                for (int i = 0; i < recordings.size(); i++) {
                    SpeakingGradingService.AnswerFeedback feedback = result.getAnswers().get(i);
                    submission.getAnswers().add(SpeakingAnswer.builder()
                            .submission(submission).questionIndex(i)
                            .audioKey(keys.get(i)).audioMimeType(recordings.get(i).mimeType())
                            .durationSeconds(recordings.get(i).durationSeconds())
                            .transcript(feedback.transcript()).comment(feedback.comment())
                            .build());
                }
            } else {
                submission.setAudioKey(keys.get(0));
                submission.setAudioMimeType(recordings.get(0).mimeType());
            }
            submission = submissionRepository.save(submission);
            scoreHistoryRepository.save(ScoreHistory.builder()
                    .user(user).skillType(SkillType.SPEAKING).score(result.getOverallBand())
                    .difficulty("PART_" + prompt.getPart())
                    .timeSpentSeconds(totalSeconds)
                    .recordedAt(submission.getSubmittedAt())
                    .build());
            return submission;
        });
        statsService.evictOverviewCache(userId);
        return toSubmissionResponse(saved);
    }

    public SpeakingSubmissionResponse getSubmission(Long userId, Long submissionId) {
        return toSubmissionResponse(findOwned(userId, submissionId));
    }

    public Page<SpeakingHistoryItemResponse> history(Long userId, int page, int size) {
        return submissionRepository.findByUserUserIdOrderBySubmittedAtDesc(userId, PageRequest.of(page, Math.min(size, 50)))
                .map(s -> SpeakingHistoryItemResponse.builder()
                        .submissionId(s.getSubmissionId())
                        .part(s.getPrompt().getPart())
                        .topic(s.getPrompt().getTopic())
                        .overallBand(s.getOverallBand())
                        .durationSeconds(s.getDurationSeconds())
                        .submittedAt(s.getSubmittedAt())
                        .build());
    }

    /** The recording and its content type, for its owner only (a submission answered in one take). */
    public Map.Entry<byte[], String> recording(Long userId, Long submissionId) {
        SpeakingSubmission submission = findOwned(userId, submissionId);
        if (submission.getAudioKey() == null) {
            throw new ResourceNotFoundException("This submission has one recording per question");
        }
        return Map.entry(storageService.downloadAudio(submission.getAudioKey()), submission.getAudioMimeType());
    }

    /** The recording of one Part 1/3 answer and its content type, for its owner only. */
    public Map.Entry<byte[], String> answerRecording(Long userId, Long submissionId, int questionIndex) {
        SpeakingAnswer answer = findOwned(userId, submissionId).getAnswers().stream()
                .filter(a -> a.getQuestionIndex() == questionIndex)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No answer to question " + (questionIndex + 1)));
        return Map.entry(storageService.downloadAudio(answer.getAudioKey()), answer.getAudioMimeType());
    }

    private SpeakingSubmission findOwned(Long userId, Long submissionId) {
        return submissionRepository.findBySubmissionIdAndUserUserId(submissionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking submission not found: " + submissionId));
    }

    /** "audio/webm;codecs=opus" → "audio/webm"; anything not a recording format is refused. */
    static String normaliseType(String contentType) {
        String base = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        if (!ACCEPTED_TYPES.contains(base)) {
            throw new IllegalArgumentException("Unsupported audio format: " + (base.isEmpty() ? "unknown" : base));
        }
        return base;
    }

    private static String extension(String mimeType) {
        return switch (mimeType) {
            case "audio/ogg" -> ".ogg";
            case "audio/mp4", "audio/aac" -> ".m4a";
            case "audio/mpeg" -> ".mp3";
            case "audio/wav", "audio/x-wav" -> ".wav";
            default -> ".webm";
        };
    }

    private String feedbackJson(SpeakingGradingService.Result result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "summary", result.getSummary(),
                    "strengths", result.getStrengths(),
                    "improvements", result.getImprovements(),
                    "criteria", result.getCriteriaComments()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not store the speaking feedback", e);
        }
    }

    static SpeakingPromptResponse toPromptResponse(SpeakingPrompt p) {
        return SpeakingPromptResponse.builder()
                .promptId(p.getPromptId())
                .part(p.getPart())
                .topic(p.getTopic())
                .questions(lines(p.getQuestionText()))
                .cuePoints(lines(p.getCuePoints()))
                .prepSeconds(prepSeconds(p.getPart()))
                .maxSpeakSeconds(maxSpeakSeconds(p.getPart()))
                .build();
    }

    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static List<SpeakingAnswerResponse> toAnswerResponses(SpeakingSubmission s) {
        List<String> questions = lines(s.getPrompt().getQuestionText());
        return s.getAnswers().stream()
                .map(a -> SpeakingAnswerResponse.builder()
                        .questionIndex(a.getQuestionIndex())
                        .question(a.getQuestionIndex() < questions.size() ? questions.get(a.getQuestionIndex()) : "")
                        .durationSeconds(a.getDurationSeconds())
                        .transcript(a.getTranscript())
                        .comment(a.getComment())
                        .build())
                .toList();
    }

    private SpeakingSubmissionResponse toSubmissionResponse(SpeakingSubmission s) {
        Map<String, Object> feedback;
        try {
            feedback = objectMapper.readValue(s.getFeedbackJson(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Unreadable speaking feedback for submission {}", s.getSubmissionId());
            feedback = Map.of();
        }
        return SpeakingSubmissionResponse.builder()
                .submissionId(s.getSubmissionId())
                .prompt(toPromptResponse(s.getPrompt()))
                .durationSeconds(s.getDurationSeconds())
                .transcript(s.getTranscript())
                .overallBand(s.getOverallBand())
                .fluencyBand(s.getFluencyBand())
                .lexicalBand(s.getLexicalBand())
                .grammarBand(s.getGrammarBand())
                .pronunciationBand(s.getPronunciationBand())
                .summary((String) feedback.getOrDefault("summary", ""))
                .strengths(castList(feedback.get("strengths")))
                .improvements(castList(feedback.get("improvements")))
                .criteriaComments(castMap(feedback.get("criteria")))
                .answers(toAnswerResponses(s))
                .submittedAt(s.getSubmittedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}
