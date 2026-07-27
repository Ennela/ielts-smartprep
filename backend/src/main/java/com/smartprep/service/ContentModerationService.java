package com.smartprep.service;

import com.smartprep.dto.response.ContentItemResponse;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private final ReadingQuizRepository readingQuizRepository;
    private final ListeningPartRepository listeningPartRepository;
    private final WritingPromptRepository writingPromptRepository;
    private final MockTestRepository mockTestRepository;

    private static final Map<ContentStatus, Set<ContentStatus>> VALID_TRANSITIONS = Map.of(
            ContentStatus.DRAFT, Set.of(ContentStatus.AI_IMPORTED, ContentStatus.HUMAN_REVIEWED),
            ContentStatus.AI_IMPORTED, Set.of(ContentStatus.HUMAN_REVIEWED, ContentStatus.DRAFT),
            ContentStatus.HUMAN_REVIEWED, Set.of(ContentStatus.PUBLISHED, ContentStatus.DRAFT),
            ContentStatus.PUBLISHED, Set.of(ContentStatus.HUMAN_REVIEWED, ContentStatus.DRAFT)
    );

    public Page<ContentItemResponse> listContent(String type, ContentStatus status, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);

        if (type == null && status == null) {
            // Return all reading quizzes as default when no filter specified
            return readingQuizRepository.findAll(pageable).map(this::toResponse);
        }

        if (type != null) {
            return switch (type.toUpperCase()) {
                case "READING" -> status != null
                        ? readingQuizRepository.findByContentStatus(status, pageable).map(this::toResponse)
                        : readingQuizRepository.findAll(pageable).map(this::toResponse);
                case "LISTENING" -> status != null
                        ? listeningPartRepository.findByContentStatus(status, pageable).map(this::toResponse)
                        : listeningPartRepository.findAll(pageable).map(this::toResponse);
                case "WRITING" -> status != null
                        ? writingPromptRepository.findByContentStatus(status, pageable).map(this::toResponse)
                        : writingPromptRepository.findAll(pageable).map(this::toResponse);
                case "MOCK_TEST" -> status != null
                        ? mockTestRepository.findByContentStatus(status, pageable).map(this::toResponse)
                        : mockTestRepository.findAll(pageable).map(this::toResponse);
                default -> throw new IllegalArgumentException("Invalid content type: " + type + ". Valid types: READING, LISTENING, WRITING, MOCK_TEST");
            };
        }

        // status != null but type == null → default to READING
        return readingQuizRepository.findByContentStatus(status, pageable).map(this::toResponse);
    }

    @Transactional
    public ContentItemResponse updateStatus(String type, Long id, ContentStatus newStatus) {
        return switch (type.toUpperCase()) {
            case "READING" -> {
                ReadingQuiz quiz = readingQuizRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Reading quiz not found: " + id));
                validateTransition(quiz.getContentStatus(), newStatus);
                quiz.setContentStatus(newStatus);
                yield toResponse(readingQuizRepository.save(quiz));
            }
            case "LISTENING" -> {
                ListeningPart part = listeningPartRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + id));
                validateTransition(part.getContentStatus(), newStatus);
                part.setContentStatus(newStatus);
                yield toResponse(listeningPartRepository.save(part));
            }
            case "WRITING" -> {
                WritingPrompt prompt = writingPromptRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found: " + id));
                validateTransition(prompt.getContentStatus(), newStatus);
                prompt.setContentStatus(newStatus);
                yield toResponse(writingPromptRepository.save(prompt));
            }
            case "MOCK_TEST" -> {
                MockTest test = mockTestRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Mock test not found: " + id));
                validateTransition(test.getContentStatus(), newStatus);
                test.setContentStatus(newStatus);
                yield toResponse(mockTestRepository.save(test));
            }
            default -> throw new IllegalArgumentException("Invalid content type: " + type);
        };
    }

    private void validateTransition(ContentStatus current, ContentStatus target) {
        Set<ContentStatus> allowed = VALID_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalArgumentException(
                    "Invalid status transition: " + current + " → " + target +
                    ". Allowed transitions from " + current + ": " + allowed);
        }
    }

    private Pageable buildPageable(int page, int size, String sort) {
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        String[] parts = sort.split(",");
        String field = parts[0];
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1])
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, field));
    }

    private ContentItemResponse toResponse(ReadingQuiz quiz) {
        return ContentItemResponse.builder()
                .id(quiz.getQuizId())
                .type("READING")
                .title(quiz.getTopic() != null ? quiz.getTopic().name() : "")
                .contentStatus(quiz.getContentStatus())
                .createdBy(quiz.getCreatedBy())
                .source(quiz.getSource())
                .createdAt(quiz.getCreatedAt())
                .build();
    }

    private ContentItemResponse toResponse(ListeningPart part) {
        return ContentItemResponse.builder()
                .id(part.getPartId())
                .type("LISTENING")
                .title(part.getTitle())
                .contentStatus(part.getContentStatus())
                .createdBy(part.getCreatedBy())
                .source(part.getSource())
                .createdAt(part.getCreatedAt())
                .build();
    }

    private ContentItemResponse toResponse(WritingPrompt prompt) {
        return ContentItemResponse.builder()
                .id(prompt.getPromptId())
                .type("WRITING")
                .title(prompt.getEssayType() != null ? prompt.getEssayType().name() : "")
                .contentStatus(prompt.getContentStatus())
                .createdBy(prompt.getCreatedBy())
                .source(prompt.getSource())
                .createdAt(prompt.getCreatedAt())
                .build();
    }

    private ContentItemResponse toResponse(MockTest test) {
        return ContentItemResponse.builder()
                .id(test.getMockTestId())
                .type("MOCK_TEST")
                .title(test.getTitle())
                .contentStatus(test.getContentStatus())
                .createdBy(test.getCreatedBy())
                .source(test.getSource())
                .createdAt(test.getCreatedAt())
                .build();
    }
}
