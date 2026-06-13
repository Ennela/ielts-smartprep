package com.smartprep.service.vocab;

import com.smartprep.dto.request.VocabBulkSaveRequest;
import com.smartprep.dto.request.VocabCreateRequest;
import com.smartprep.dto.response.VocabResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.VocabularyRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
    private final MockTestSubmissionRepository mockTestSubmissionRepository;
    private final Sm2Service sm2Service;
    private final VocabAiService vocabAiService;
    private final List<VocabSourceResolver> resolvers;

    @Transactional
    public VocabResponse addVocabulary(Long userId, VocabCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String word = request.getWord().trim();
        Optional<Vocabulary> existing = vocabularyRepository.findByUserUserIdAndWord(userId, word);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Từ vựng '" + word + "' đã tồn tại trong danh mục ôn tập của bạn.");
        }

        SkillType sourceSkill = null;
        if (request.getSourceSkill() != null && !request.getSourceSkill().isBlank()) {
            try {
                sourceSkill = SkillType.valueOf(request.getSourceSkill().toUpperCase());
            } catch (Exception e) {
                log.warn("Invalid source skill: {}", request.getSourceSkill());
            }
        }

        Vocabulary vocab = Vocabulary.builder()
                .user(user)
                .word(word)
                .phonetic(request.getPhonetic())
                .partOfSpeech(request.getPartOfSpeech())
                .meaningVi(request.getMeaningVi())
                .example(request.getExample())
                .collocation(request.getCollocation())
                .sourceSkill(sourceSkill)
                .sourceRef(request.getSourceRef())
                .cefrLevel(request.getCefrLevel())
                .dueDate(LocalDateTime.now()) // Set due date to now to enter SRS queue immediately
                .build();

        vocab = vocabularyRepository.save(vocab);
        return toResponse(vocab);
    }

    @Transactional(readOnly = true)
    public List<VocabResponse> getDueVocabularies(Long userId) {
        return vocabularyRepository.findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(userId, LocalDateTime.now())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<VocabResponse> getDueVocabularies(Long userId, Pageable pageable) {
        return vocabularyRepository.findByUserUserIdAndDueDateBefore(userId, LocalDateTime.now(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<VocabResponse> getAllVocabularies(Long userId) {
        return vocabularyRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public VocabResponse reviewVocabulary(Long userId, Long vocabId, String grade) {
        Vocabulary vocab = vocabularyRepository.findById(vocabId)
                .orElseThrow(() -> new ResourceNotFoundException("Vocabulary item not found"));

        if (!vocab.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to review this vocabulary item");
        }

        sm2Service.updateReview(vocab, grade);
        vocab = vocabularyRepository.save(vocab);
        return toResponse(vocab);
    }

    @Transactional(readOnly = true)
    public List<VocabAiService.SuggestedVocab> suggestVocabulary(Long userId, String skillTypeStr, Long sourceId) {
        Set<String> existingWords = vocabularyRepository.findByUserUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(v -> v.getWord().toLowerCase().trim())
                .collect(Collectors.toSet());

        if (skillTypeStr.equalsIgnoreCase("MOCK")) {
            MockTestSubmission submission = mockTestSubmissionRepository.findById(sourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mock test submission not found with ID: " + sourceId));
            
            List<String> sectionsTexts = new java.util.ArrayList<>();
            
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
            
            List<VocabAiService.SuggestedVocab> aggregated = new java.util.ArrayList<>();
            for (String sectionText : sectionsTexts) {
                try {
                    List<VocabAiService.SuggestedVocab> sectionSuggestions = vocabAiService.suggestVocabulary(sectionText);
                    if (sectionSuggestions != null) {
                        aggregated.addAll(sectionSuggestions);
                    }
                } catch (Exception e) {
                    log.error("Failed to suggest vocabulary for mock test section text", e);
                }
            }
            
            return filterAndDeduplicate(aggregated, existingWords);
        }

        SkillType skillType;
        try {
            skillType = SkillType.valueOf(skillTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid skill type: " + skillTypeStr);
        }

        VocabSourceResolver resolver = resolvers.stream()
                .filter(r -> r.getSkillType() == skillType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No source resolver found for skill: " + skillType));

        String text = resolver.resolveSourceText(sourceId);
        List<VocabAiService.SuggestedVocab> rawSuggestions = vocabAiService.suggestVocabulary(text);
        return filterAndDeduplicate(rawSuggestions, existingWords);
    }

    private List<VocabAiService.SuggestedVocab> filterAndDeduplicate(
            List<VocabAiService.SuggestedVocab> list, Set<String> existingWords) {
        if (list == null) {
            return Collections.emptyList();
        }
        
        Set<String> seenInResult = new HashSet<>();
        return list.stream()
                .filter(item -> item.getWord() != null && !item.getWord().isBlank())
                .filter(item -> item.getMeaningVi() != null && !item.getMeaningVi().isBlank())
                .filter(item -> {
                    String cleanWord = item.getWord().toLowerCase().trim();
                    return !existingWords.contains(cleanWord) && seenInResult.add(cleanWord);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public int bulkSaveVocabulary(Long userId, VocabBulkSaveRequest request) {
        if (request == null || request.getVocabularies() == null || request.getVocabularies().isEmpty()) {
            return 0;
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int savedCount = 0;
        for (VocabCreateRequest item : request.getVocabularies()) {
            String word = item.getWord().trim();
            Optional<Vocabulary> existing = vocabularyRepository.findByUserUserIdAndWord(userId, word);

            SkillType sourceSkill = null;
            if (item.getSourceSkill() != null && !item.getSourceSkill().isBlank()) {
                try {
                    sourceSkill = SkillType.valueOf(item.getSourceSkill().toUpperCase());
                } catch (Exception e) {
                    log.warn("Invalid source skill in bulk save: {}", item.getSourceSkill());
                }
            }

            if (existing.isPresent()) {
                Vocabulary vocab = existing.get();
                if (vocab.getMeaningVi() == null || vocab.getMeaningVi().isBlank()) {
                    vocab.setMeaningVi(item.getMeaningVi());
                }
                if (vocab.getPhonetic() == null || vocab.getPhonetic().isBlank()) {
                    vocab.setPhonetic(item.getPhonetic());
                }
                if (vocab.getExample() == null || vocab.getExample().isBlank()) {
                    vocab.setExample(item.getExample());
                }
                if (vocab.getCollocation() == null || vocab.getCollocation().isBlank()) {
                    vocab.setCollocation(item.getCollocation());
                }
                if (vocab.getCefrLevel() == null || vocab.getCefrLevel().isBlank()) {
                    vocab.setCefrLevel(item.getCefrLevel());
                }
                if (sourceSkill != null) {
                    vocab.setSourceSkill(sourceSkill);
                }
                if (item.getSourceRef() != null && !item.getSourceRef().isBlank()) {
                    vocab.setSourceRef(item.getSourceRef());
                }
                vocabularyRepository.save(vocab);
                savedCount++;
                continue;
            }

            Vocabulary vocab = Vocabulary.builder()
                    .user(user)
                    .word(word)
                    .phonetic(item.getPhonetic())
                    .partOfSpeech(item.getPartOfSpeech())
                    .meaningVi(item.getMeaningVi())
                    .example(item.getExample())
                    .collocation(item.getCollocation())
                    .sourceSkill(sourceSkill)
                    .sourceRef(item.getSourceRef())
                    .cefrLevel(item.getCefrLevel())
                    .dueDate(LocalDateTime.now())
                    .build();

            vocabularyRepository.save(vocab);
            savedCount++;
        }

        return savedCount;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getVocabStats(Long userId) {
        long mastered = vocabularyRepository.countByUserUserIdAndRepetitionsGreaterThanEqual(userId, 4);
        long learning = vocabularyRepository.countByUserUserIdAndRepetitionsLessThan(userId, 4);
        long dueToday = vocabularyRepository.countByUserUserIdAndDueDateBefore(userId, LocalDateTime.now());
        
        return java.util.Map.of(
            "masteredCount", mastered,
            "learningCount", learning,
            "dueTodayCount", dueToday
        );
    }

    @Transactional
    public void deleteVocabulary(Long userId, Long vocabId) {
        Vocabulary vocab = vocabularyRepository.findById(vocabId)
                .orElseThrow(() -> new ResourceNotFoundException("Vocabulary item not found"));

        if (!vocab.getUser().getUserId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to delete this vocabulary item");
        }

        vocabularyRepository.delete(vocab);
    }

    private VocabResponse toResponse(Vocabulary vocab) {
        return VocabResponse.builder()
                .vocabId(vocab.getVocabId())
                .word(vocab.getWord())
                .phonetic(vocab.getPhonetic())
                .partOfSpeech(vocab.getPartOfSpeech())
                .meaningVi(vocab.getMeaningVi())
                .example(vocab.getExample())
                .collocation(vocab.getCollocation())
                .easeFactor(vocab.getEaseFactor())
                .intervalDays(vocab.getIntervalDays())
                .repetitions(vocab.getRepetitions())
                .dueDate(vocab.getDueDate())
                .sourceSkill(vocab.getSourceSkill() != null ? vocab.getSourceSkill().name() : null)
                .sourceRef(vocab.getSourceRef())
                .cefrLevel(vocab.getCefrLevel())
                .build();
    }
}
