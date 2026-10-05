package com.smartprep.service.vocab;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.ListeningTest;
import com.smartprep.model.entity.ListeningTestPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingSubmission;
import com.smartprep.repository.MockTestSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The text of a learner's mock test sitting, one entry per section, for vocabulary suggestions.
 *
 * <p>A bean of its own so the walk through the sitting -- reading quizzes, listening parts,
 * writing submissions, all lazy -- runs in one short read-only transaction and returns
 * plain strings. With open-in-view off nothing else keeps a session open for it, and the
 * Gemini calls that follow must not run inside a transaction.
 */
@Component
@RequiredArgsConstructor
public class MockTestVocabSource {

    private final MockTestSubmissionRepository mockTestSubmissionRepository;

    @Transactional(readOnly = true)
    public List<String> sectionTexts(Long userId, Long submissionId) {
        MockTestSubmission submission = mockTestSubmissionRepository.findById(submissionId)
                .filter(s -> s.getUser() != null && s.getUser().getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Mock test submission not found with ID: " + submissionId));

        List<String> sectionsTexts = new ArrayList<>();

        // 1. Reading
        MockTest mockTest = submission.getMockTest();
        if (mockTest != null && mockTest.getReadingQuizzes() != null) {
            for (ReadingQuiz quiz : mockTest.getReadingQuizzes()) {
                if (quiz.getPassageText() != null && !quiz.getPassageText().isBlank()) {
                    sectionsTexts.add(quiz.getPassageText());
                }
            }
        }

        // 2. Listening
        ListeningTest listeningTest = submission.getListeningTest();
        if (listeningTest != null && listeningTest.getTestParts() != null && !listeningTest.getTestParts().isEmpty()) {
            for (ListeningTestPart tp : listeningTest.getTestParts()) {
                if (tp.getPart() != null && tp.getPart().getTranscriptText() != null && !tp.getPart().getTranscriptText().isBlank()) {
                    sectionsTexts.add(tp.getPart().getTranscriptText());
                }
            }
        } else if (mockTest != null && mockTest.getListeningParts() != null) {
            for (ListeningPart part : mockTest.getListeningParts()) {
                if (part.getTranscriptText() != null && !part.getTranscriptText().isBlank()) {
                    sectionsTexts.add(part.getTranscriptText());
                }
            }
        }

        // 3. Writing
        WritingSubmission w1 = submission.getWritingTask1Submission();
        if (w1 != null) {
            String promptText = (w1.getPrompt() != null) ? w1.getPrompt().getPromptText() : "";
            String essayText = (w1.getEssayText() != null) ? w1.getEssayText() : "";
            sectionsTexts.add("Prompt:\n" + promptText + "\n\nStudent Essay:\n" + essayText);
        }
        WritingSubmission w2 = submission.getWritingTask2Submission();
        if (w2 != null) {
            String promptText = (w2.getPrompt() != null) ? w2.getPrompt().getPromptText() : "";
            String essayText = (w2.getEssayText() != null) ? w2.getEssayText() : "";
            sectionsTexts.add("Prompt:\n" + promptText + "\n\nStudent Essay:\n" + essayText);
        }
        return sectionsTexts;
    }
}
