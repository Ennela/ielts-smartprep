package com.smartprep.service;

import com.smartprep.dto.response.ListeningHistoryResponse;
import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.dto.response.QuestionOptionResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.ListeningTestRepository;
import com.smartprep.repository.ScoreHistoryRepository;
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
}
