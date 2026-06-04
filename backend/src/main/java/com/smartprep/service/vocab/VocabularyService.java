package com.smartprep.service.vocab;

import com.smartprep.dto.request.VocabBulkSaveRequest;
import com.smartprep.dto.request.VocabCreateRequest;
import com.smartprep.dto.response.VocabResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.Vocabulary;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.VocabularyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VocabularyService {

    private final VocabularyRepository vocabularyRepository;
    private final UserRepository userRepository;
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
    public List<VocabAiService.SuggestedVocab> suggestVocabulary(String skillTypeStr, Long sourceId) {
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
        return vocabAiService.suggestVocabulary(text);
    }

    @Transactional
    public int bulkSaveVocabulary(Long userId, VocabBulkSaveRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        int savedCount = 0;
        for (VocabCreateRequest item : request.getVocabularies()) {
            String word = item.getWord().trim();
            Optional<Vocabulary> existing = vocabularyRepository.findByUserUserIdAndWord(userId, word);

            if (existing.isPresent()) {
                // Skip duplicates
                log.info("Skipping duplicate word: {}", word);
                continue;
            }

            SkillType sourceSkill = null;
            if (item.getSourceSkill() != null && !item.getSourceSkill().isBlank()) {
                try {
                    sourceSkill = SkillType.valueOf(item.getSourceSkill().toUpperCase());
                } catch (Exception e) {
                    log.warn("Invalid source skill in bulk save: {}", item.getSourceSkill());
                }
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
                    .dueDate(LocalDateTime.now()) // Set due date to today to enter SRS queue immediately
                    .build();

            vocabularyRepository.save(vocab);
            savedCount++;
        }

        return savedCount;
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
