package com.smartprep.service;

import com.smartprep.dto.response.ListeningHistoryResponse;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.dto.response.ListeningTestResponse;
import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only queries, mock test assembly, and DTO mapping for Listening.
 * Extracted from the original ListeningService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningQueryService {

    private final ListeningPartRepository partRepository;
    private final ListeningTestRepository testRepository;
    private final ScoreHistoryRepository scoreHistoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ListeningPartResponse> getAllParts() {
        return partRepository.findAllByOrderByPartNumberAscPartIdAsc().stream()
                .map(this::toPartResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListeningPartResponse getPartById(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found"));
        return toPartResponse(part);
    }

    @Transactional(readOnly = true)
    public List<ListeningPartResponse> assembleMockTest(Long userId) {
        List<Long> recentPartIds = testRepository.findRecentPartIds(userId, LocalDateTime.now().minusDays(7));
        List<ListeningPartResponse> selectedParts = new ArrayList<>();
        Random random = new Random();

        for (int partNum = 1; partNum <= 4; partNum++) {
            List<ListeningPart> candidates = partRepository.findByPartNumberOrderByPartIdAsc(partNum);
            List<ListeningPart> filtered = candidates.stream()
                    .filter(p -> !recentPartIds.contains(p.getPartId()))
                    .collect(Collectors.toList());
            if (filtered.isEmpty()) filtered = candidates;
            if (filtered.isEmpty()) continue;

            ListeningPart chosen = filtered.get(random.nextInt(filtered.size()));
            selectedParts.add(toPartResponse(chosen));
        }
        return selectedParts;
    }

    @Transactional(readOnly = true)
    public List<ListeningHistoryResponse> getHistory(Long userId) {
        List<ScoreHistory> listeningHistories = scoreHistoryRepository
                .findByUserUserIdOrderByRecordedAtDesc(userId).stream()
                .filter(sh -> sh.getSkillType() == SkillType.LISTENING)
                .collect(Collectors.toList());

        return testRepository.findByUserUserIdOrderBySubmittedAtDesc(userId).stream()
                .map(t -> {
                    Long historyId = listeningHistories.stream()
                            .filter(sh -> sh.getScore().compareTo(t.getScore()) == 0)
                            .filter(sh -> !sh.getRecordedAt().isBefore(t.getSubmittedAt().minusSeconds(5)))
                            .filter(sh -> !sh.getRecordedAt().isAfter(t.getSubmittedAt().plusSeconds(5)))
                            .map(ScoreHistory::getHistoryId)
                            .findFirst().orElse(null);

                    return ListeningHistoryResponse.builder()
                            .testId(t.getTestId()).historyId(historyId)
                            .testMode(t.getTestMode().name()).score(t.getScore())
                            .totalQuestions(t.getTotalQuestions()).correctAnswers(t.getCorrectAnswers())
                            .submittedAt(t.getSubmittedAt()).build();
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Mapping helpers (package-visible for sibling services)
    // =========================================================================

    public ListeningPartResponse toPartResponse(ListeningPart part) {
        List<ListeningPartResponse.QuestionDto> questions = part.getQuestions().stream()
                .sorted(Comparator.comparingInt(ListeningQuestion::getOrderIndex))
                .map(q -> ListeningPartResponse.QuestionDto.builder()
                        .questionId(q.getQuestionId())
                        .questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(mapOptions(q.getOptions(), false))
                        .orderIndex(q.getOrderIndex())
                        .build())
                .collect(Collectors.toList());

        return ListeningPartResponse.builder()
                .partId(part.getPartId()).partNumber(part.getPartNumber())
                .title(part.getTitle()).topic(part.getTopic())
                .audioUrl(part.getAudioUrl())
                .audioStatus(part.getAudioStatus() != null ? part.getAudioStatus().name() : AudioStatus.PENDING.name())
                .durationSeconds(part.getDurationSeconds())
                .questionCount(questions.size()).questions(questions).build();
    }

    public List<QuestionOptionResponse> mapOptions(List<QuestionOption> options, boolean showCorrect) {
        if (options == null) return null;
        return options.stream()
                .map(o -> QuestionOptionResponse.builder()
                        .optionId(o.getOptionId()).label(o.getLabel())
                        .content(o.getContent())
                        .isCorrect(showCorrect ? o.getIsCorrect() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ListeningTestResponse getTestResult(Long testId) {
        ListeningTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening test not found"));

        List<ListeningTestResponse.PartResult> partResults = new ArrayList<>();
        int totalQuestions = 0;
        int correctCount = 0;

        for (ListeningTestPart testPart : test.getTestParts()) {
            ListeningPart part = testPart.getPart();
            Map<String, String> answersMap = new HashMap<>();
            try {
                if (testPart.getUserAnswersJson() != null) {
                    answersMap = objectMapper.readValue(testPart.getUserAnswersJson(), Map.class);
                }
            } catch (Exception e) {
                log.warn("Failed to parse user answers JSON for test part {}: {}", testPart.getId(), e.getMessage());
            }

            List<ListeningTestResponse.QuestionResult> questionResults = new ArrayList<>();
            for (ListeningQuestion q : part.getQuestions()) {
                totalQuestions++;
                // JSON keys are strings when deserialized into Map.class
                String userAnswer = answersMap.get(String.valueOf(q.getQuestionId()));
                if (userAnswer == null) {
                    userAnswer = answersMap.getOrDefault(q.getQuestionId(), "");
                }
                boolean isCorrect = checkAnswer(q.getCorrectAnswer(), userAnswer, q.getQuestionType().name());
                if (isCorrect) correctCount++;

                questionResults.add(ListeningTestResponse.QuestionResult.builder()
                        .questionId(q.getQuestionId()).questionType(q.getQuestionType().name())
                        .questionText(q.getQuestionText())
                        .options(mapOptions(q.getOptions(), true))
                        .correctAnswer(q.getCorrectAnswer()).userAnswer(userAnswer)
                        .isCorrect(isCorrect).orderIndex(q.getOrderIndex()).build());
            }

            partResults.add(ListeningTestResponse.PartResult.builder()
                    .partId(part.getPartId()).partNumber(part.getPartNumber())
                    .title(part.getTitle()).topic(part.getTopic())
                    .audioUrl(part.getAudioUrl()).transcriptText(part.getTranscriptText())
                    .questions(questionResults).build());
        }

        // Sort parts by part number
        partResults.sort(Comparator.comparingInt(ListeningTestResponse.PartResult::getPartNumber));

        return ListeningTestResponse.builder()
                .testId(test.getTestId())
                .testMode(test.getTestMode().name())
                .score(test.getScore())
                .totalQuestions(test.getTotalQuestions())
                .correctAnswers(test.getCorrectAnswers())
                .submittedAt(test.getSubmittedAt())
                .parts(partResults)
                .build();
    }

    private boolean checkAnswer(String correct, String userAnswer, String questionType) {
        if (userAnswer == null || userAnswer.isBlank()) return false;
        String cleanCorrect = correct.trim().toLowerCase();
        String cleanUser = userAnswer.trim().toLowerCase();
        if ("MCQ".equals(questionType)) return cleanCorrect.equals(cleanUser);
        if (cleanCorrect.equals(cleanUser)) return true;
        return normalizeSpelling(cleanCorrect).equals(normalizeSpelling(cleanUser));
    }

    private String normalizeSpelling(String s) {
        return s.replace("organisation", "organization").replace("centre", "center")
                .replace("colour", "color").replace("favour", "favor")
                .replace("behaviour", "behavior").replace("travelling", "traveling")
                .replace("cancelled", "canceled")
                .replaceAll("[^a-z0-9\\s]", "").replaceAll("\\s+", " ").trim();
    }
}
