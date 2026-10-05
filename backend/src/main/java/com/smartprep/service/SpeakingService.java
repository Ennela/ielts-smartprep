package com.smartprep.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.SpeakingHistoryItemResponse;
import com.smartprep.dto.response.SpeakingPromptResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Speaking practice: the prompts, grading a recorded answer, and the learner's results.
 *
 * Grading calls Gemini, which can take tens of seconds, so it runs outside any transaction
 * (no database connection is held while waiting); the recording, the submission and its
 * score_history row are then written together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeakingService {

    /** Formats MediaRecorder produces in current browsers, plus common uploads. */
    static final Set<String> ACCEPTED_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/aac");
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MIN_SECONDS = 5;

    private final SpeakingPromptRepository promptRepository;
    private final SpeakingSubmissionRepository submissionRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final UserRepository userRepository;
    private final SpeakingGradingService gradingService;
    private final StorageService storageService;
    private final StatsService statsService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** Part 2 has a minute to prepare and two to speak; Parts 1 and 3 are answered at once. */
    static int prepSeconds(int part) {
        return part == 2 ? 60 : 0;
    }

    static int maxSpeakSeconds(int part) {
        return switch (part) {
            case 1 -> 120;
            case 2 -> 120;
            default -> 180;
        };
    }

    public List<SpeakingPromptResponse> listPrompts(Integer part) {
        List<SpeakingPrompt> prompts = part == null
                ? promptRepository.findAllByOrderByPartAscPromptIdAsc()
                : promptRepository.findByPartOrderByPromptIdAsc(part);
        return prompts.stream().map(SpeakingService::toPromptResponse).toList();
    }

    public SpeakingSubmissionResponse grade(Long userId, Long promptId, byte[] audio, String contentType,
                                            int durationSeconds) {
        SpeakingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Speaking prompt not found: " + promptId));
        String mimeType = normaliseType(contentType);
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("The recording is empty");
        }
        if (audio.length > MAX_BYTES) {
            throw new IllegalArgumentException("The recording is larger than 5 MB");
        }
        int maxSeconds = maxSpeakSeconds(prompt.getPart());
        if (durationSeconds < MIN_SECONDS) {
            throw new IllegalArgumentException("The recording is too short to grade (at least " + MIN_SECONDS + " seconds)");
        }
        // A little slack: the browser stops the recorder at the limit, but its clock and ours differ.
        if (durationSeconds > maxSeconds + 10) {
            throw new IllegalArgumentException("Part " + prompt.getPart() + " answers are at most " + maxSeconds + " seconds");
        }

        SpeakingGradingService.Result result = gradingService.grade(prompt, audio, mimeType, durationSeconds);

        String key = "speaking_" + userId + "_" + System.currentTimeMillis() + extension(mimeType);
        storageService.uploadRecording(key, audio, mimeType);

        SpeakingSubmission saved = transactionTemplate.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            SpeakingSubmission submission = submissionRepository.save(SpeakingSubmission.builder()
                    .user(user).prompt(prompt)
                    .audioKey(key).audioMimeType(mimeType).durationSeconds(durationSeconds)
                    .transcript(result.getTranscript())
                    .overallBand(result.getOverallBand())
                    .fluencyBand(result.getFluencyBand()).lexicalBand(result.getLexicalBand())
                    .grammarBand(result.getGrammarBand()).pronunciationBand(result.getPronunciationBand())
                    .feedbackJson(feedbackJson(result))
                    .build());
            scoreHistoryRepository.save(ScoreHistory.builder()
                    .user(user).skillType(SkillType.SPEAKING).score(result.getOverallBand())
                    .difficulty("PART_" + prompt.getPart())
                    .timeSpentSeconds(durationSeconds)
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

    /** The recording and its content type, for its owner only. */
    public Map.Entry<byte[], String> recording(Long userId, Long submissionId) {
        SpeakingSubmission submission = findOwned(userId, submissionId);
        return Map.entry(storageService.downloadAudio(submission.getAudioKey()), submission.getAudioMimeType());
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
