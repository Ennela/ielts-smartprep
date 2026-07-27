package com.smartprep.service;

import com.smartprep.dto.response.ContentItemResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.entity.MockTest;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.*;
import com.smartprep.repository.ListeningPartRepository;
import com.smartprep.repository.MockTestRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.WritingPromptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentModerationServiceTest {

    @Mock private ReadingQuizRepository readingQuizRepository;
    @Mock private ListeningPartRepository listeningPartRepository;
    @Mock private WritingPromptRepository writingPromptRepository;
    @Mock private MockTestRepository mockTestRepository;

    @InjectMocks private ContentModerationService contentModerationService;

    // =========================================================================
    // Status Transitions
    // =========================================================================
    @Nested
    @DisplayName("Status transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("DRAFT → AI_IMPORTED: valid")
        void draftToAiImported_valid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.DRAFT);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(readingQuizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("READING", 1L, ContentStatus.AI_IMPORTED);

            assertEquals(ContentStatus.AI_IMPORTED, result.getContentStatus());
            verify(readingQuizRepository).save(quiz);
        }

        @Test
        @DisplayName("DRAFT → HUMAN_REVIEWED: valid")
        void draftToHumanReviewed_valid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.DRAFT);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(readingQuizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("READING", 1L, ContentStatus.HUMAN_REVIEWED);

            assertEquals(ContentStatus.HUMAN_REVIEWED, result.getContentStatus());
        }

        @Test
        @DisplayName("AI_IMPORTED → HUMAN_REVIEWED: valid")
        void aiImportedToHumanReviewed_valid() {
            ListeningPart part = buildListeningPart(ContentStatus.AI_IMPORTED);
            when(listeningPartRepository.findById(1L)).thenReturn(Optional.of(part));
            when(listeningPartRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("LISTENING", 1L, ContentStatus.HUMAN_REVIEWED);

            assertEquals(ContentStatus.HUMAN_REVIEWED, result.getContentStatus());
        }

        @Test
        @DisplayName("AI_IMPORTED → DRAFT: valid (reject back)")
        void aiImportedToDraft_valid() {
            WritingPrompt prompt = buildWritingPrompt(ContentStatus.AI_IMPORTED);
            when(writingPromptRepository.findById(1L)).thenReturn(Optional.of(prompt));
            when(writingPromptRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("WRITING", 1L, ContentStatus.DRAFT);

            assertEquals(ContentStatus.DRAFT, result.getContentStatus());
        }

        @Test
        @DisplayName("HUMAN_REVIEWED → PUBLISHED: valid")
        void humanReviewedToPublished_valid() {
            MockTest test = buildMockTest(ContentStatus.HUMAN_REVIEWED);
            when(mockTestRepository.findById(1L)).thenReturn(Optional.of(test));
            when(mockTestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("MOCK_TEST", 1L, ContentStatus.PUBLISHED);

            assertEquals(ContentStatus.PUBLISHED, result.getContentStatus());
        }

        @Test
        @DisplayName("PUBLISHED → HUMAN_REVIEWED: valid (unpublish for re-review)")
        void publishedToHumanReviewed_valid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.PUBLISHED);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(readingQuizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("READING", 1L, ContentStatus.HUMAN_REVIEWED);

            assertEquals(ContentStatus.HUMAN_REVIEWED, result.getContentStatus());
        }

        @Test
        @DisplayName("PUBLISHED → DRAFT: valid (full reset)")
        void publishedToDraft_valid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.PUBLISHED);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(readingQuizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("READING", 1L, ContentStatus.DRAFT);

            assertEquals(ContentStatus.DRAFT, result.getContentStatus());
        }

        @Test
        @DisplayName("HUMAN_REVIEWED → DRAFT: valid (reject back)")
        void humanReviewedToDraft_valid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.HUMAN_REVIEWED);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));
            when(readingQuizRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            ContentItemResponse result = contentModerationService.updateStatus("READING", 1L, ContentStatus.DRAFT);

            assertEquals(ContentStatus.DRAFT, result.getContentStatus());
        }
    }

    // =========================================================================
    // Invalid Transitions
    // =========================================================================
    @Nested
    @DisplayName("Invalid transitions")
    class InvalidTransitionTests {

        @Test
        @DisplayName("DRAFT → PUBLISHED: invalid (must go through review)")
        void draftToPublished_invalid() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.DRAFT);
            when(readingQuizRepository.findById(1L)).thenReturn(Optional.of(quiz));

            assertThrows(IllegalArgumentException.class,
                    () -> contentModerationService.updateStatus("READING", 1L, ContentStatus.PUBLISHED));
            verify(readingQuizRepository, never()).save(any());
        }

        @Test
        @DisplayName("AI_IMPORTED → PUBLISHED: invalid (must go through review)")
        void aiImportedToPublished_invalid() {
            ListeningPart part = buildListeningPart(ContentStatus.AI_IMPORTED);
            when(listeningPartRepository.findById(1L)).thenReturn(Optional.of(part));

            assertThrows(IllegalArgumentException.class,
                    () -> contentModerationService.updateStatus("LISTENING", 1L, ContentStatus.PUBLISHED));
            verify(listeningPartRepository, never()).save(any());
        }

        @Test
        @DisplayName("PUBLISHED → AI_IMPORTED: invalid")
        void publishedToAiImported_invalid() {
            WritingPrompt prompt = buildWritingPrompt(ContentStatus.PUBLISHED);
            when(writingPromptRepository.findById(1L)).thenReturn(Optional.of(prompt));

            assertThrows(IllegalArgumentException.class,
                    () -> contentModerationService.updateStatus("WRITING", 1L, ContentStatus.AI_IMPORTED));
        }
    }

    // =========================================================================
    // Listing
    // =========================================================================
    @Nested
    @DisplayName("Content listing")
    class ListingTests {

        @Test
        @DisplayName("List reading content by status")
        void listReadingByStatus() {
            ReadingQuiz quiz = buildReadingQuiz(ContentStatus.AI_IMPORTED);
            Page<ReadingQuiz> page = new PageImpl<>(List.of(quiz));
            when(readingQuizRepository.findByContentStatus(eq(ContentStatus.AI_IMPORTED), any(Pageable.class)))
                    .thenReturn(page);

            Page<ContentItemResponse> result = contentModerationService.listContent(
                    "READING", ContentStatus.AI_IMPORTED, 0, 20, "createdAt,desc");

            assertEquals(1, result.getContent().size());
            assertEquals("READING", result.getContent().get(0).getType());
            assertEquals(ContentStatus.AI_IMPORTED, result.getContent().get(0).getContentStatus());
        }

        @Test
        @DisplayName("List listening content without status filter")
        void listListeningNoStatusFilter() {
            ListeningPart part = buildListeningPart(ContentStatus.PUBLISHED);
            Page<ListeningPart> page = new PageImpl<>(List.of(part));
            when(listeningPartRepository.findAll(any(Pageable.class))).thenReturn(page);

            Page<ContentItemResponse> result = contentModerationService.listContent(
                    "LISTENING", null, 0, 20, "createdAt,desc");

            assertEquals(1, result.getContent().size());
            assertEquals("LISTENING", result.getContent().get(0).getType());
        }

        @Test
        @DisplayName("Invalid type throws IllegalArgumentException")
        void invalidType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> contentModerationService.listContent("SPEAKING", null, 0, 20, null));
        }
    }

    // =========================================================================
    // Not found
    // =========================================================================
    @Nested
    @DisplayName("Entity not found")
    class NotFoundTests {

        @Test
        @DisplayName("Update status for non-existent entity throws ResourceNotFoundException")
        void updateStatus_notFound() {
            when(readingQuizRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> contentModerationService.updateStatus("READING", 999L, ContentStatus.AI_IMPORTED));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================
    private ReadingQuiz buildReadingQuiz(ContentStatus status) {
        return ReadingQuiz.builder()
                .quizId(1L)
                .topic(Topic.TECHNOLOGY)
                .difficulty(Difficulty.PASSAGE_1)
                .passageText("Test passage")
                .contentStatus(status)
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ListeningPart buildListeningPart(ContentStatus status) {
        return ListeningPart.builder()
                .partId(1L)
                .partNumber(1)
                .title("Test Part")
                .audioUrl("http://example.com/audio.mp3")
                .contentStatus(status)
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private WritingPrompt buildWritingPrompt(ContentStatus status) {
        return WritingPrompt.builder()
                .promptId(1L)
                .promptText("Test prompt")
                .essayType(EssayType.OPINION)
                .contentStatus(status)
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MockTest buildMockTest(ContentStatus status) {
        return MockTest.builder()
                .mockTestId(1L)
                .title("Test Mock")
                .difficulty(Difficulty.PASSAGE_1)
                .contentStatus(status)
                .createdBy("SYSTEM")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
