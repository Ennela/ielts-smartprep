package com.smartprep.service.ai;

import com.smartprep.dto.response.ListeningPartResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningQuestion;
import com.smartprep.model.entity.QuestionOption;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.service.ListeningQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The published part Listening generation falls back to when Gemini fails.
 *
 * <p>A bean of its own so each read runs in a short read-only transaction: a part's
 * questions and options are lazy, and with open-in-view off nothing else keeps a session
 * open for them. The generation service itself stays non-transactional, so no connection
 * is held across a Gemini call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListeningFallbackSource {

    private final ListeningPartRepository partRepository;
    private final ListeningQueryService listeningQueryService;

    /** A published part as the response for a single generated part; rethrows the AI failure if there is none. */
    @Transactional(readOnly = true)
    public ListeningPartResponse publishedPart(int partNumber, String topic, Exception originalException) {
        ListeningPart selected = select(partNumber, topic);
        if (selected == null) {
            log.error("No fallback listening parts found in the database. Failing request.");
            if (originalException instanceof RuntimeException) {
                throw (RuntimeException) originalException;
            }
            throw new RuntimeException("AI Content generation failed and no fallback content was available in the system.", originalException);
        }
        log.info("Selected fallback ListeningPart ID: {}", selected.getPartId());
        return listeningQueryService.toPartResponse(selected);
    }

    /** An unsaved copy of a published part, with its questions and options, for a generated full test. */
    @Transactional(readOnly = true)
    public ListeningPart copyOfPublishedPart(int partNumber, String topic) {
        ListeningPart selected = select(partNumber, topic);
        if (selected == null) {
            throw new RuntimeException("No fallback parts found in database");
        }

        ListeningPart clone = ListeningPart.builder()
                .partNumber(selected.getPartNumber())
                .title(selected.getTitle() + " (AI Fallback)")
                .topic(selected.getTopic())
                .audioUrl(selected.getAudioUrl())
                .audioStatus(selected.getAudioStatus())
                .transcriptText(selected.getTranscriptText())
                .durationSeconds(selected.getDurationSeconds())
                .build();
        List<ListeningQuestion> clonedQuestions = new ArrayList<>();
        for (ListeningQuestion q : selected.getQuestions()) {
            ListeningQuestion cq = ListeningQuestion.builder()
                    .part(clone)
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .correctAnswer(q.getCorrectAnswer())
                    .orderIndex(q.getOrderIndex())
                    .build();
            List<QuestionOption> clonedOptions = new ArrayList<>();
            for (QuestionOption opt : q.getOptions()) {
                clonedOptions.add(QuestionOption.builder()
                        .listeningQuestion(cq)
                        .label(opt.getLabel())
                        .content(opt.getContent())
                        .isCorrect(opt.getIsCorrect())
                        .orderIndex(opt.getOrderIndex())
                        .build());
            }
            cq.setOptions(clonedOptions);
            clonedQuestions.add(cq);
        }
        clone.setQuestions(clonedQuestions);
        return clone;
    }

    /** A published part of this number (any number if none), preferring one on the topic; null if there are none. */
    private ListeningPart select(int partNumber, String topic) {
        List<ListeningPart> existingParts = partRepository.findPublishedByPartNumber(partNumber);
        if (existingParts.isEmpty()) {
            existingParts = partRepository.findPublishedOrderByPartNumber();
        }
        if (existingParts.isEmpty()) {
            return null;
        }
        ListeningPart selected = null;
        if (topic != null && !topic.isBlank()) {
            String cleanTopic = topic.toLowerCase();
            selected = existingParts.stream()
                    .filter(p -> p.getTopic() != null && p.getTopic().toLowerCase().contains(cleanTopic))
                    .findFirst()
                    .orElse(null);
        }
        if (selected == null) {
            selected = existingParts.get(new Random().nextInt(existingParts.size()));
        }
        return selected;
    }
}
