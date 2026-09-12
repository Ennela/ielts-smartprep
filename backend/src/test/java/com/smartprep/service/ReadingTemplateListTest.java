package com.smartprep.service;

import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.model.entity.ReadingQuestion;
import com.smartprep.model.entity.ReadingQuiz;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuestionRepository;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.service.util.UserPageRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The template catalogue must be assembled without touching a quiz's questions.
 *
 * <p>That is the whole fix: mapping each quiz through the full response walked every
 * question and option -- roughly 280 queries for a page of 20 papers. The fixture's
 * quizzes therefore fail loudly if their question list is read at all.
 */
@ExtendWith(MockitoExtension.class)
class ReadingTemplateListTest {

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private ReadingQuestionRepository questionRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;

    @InjectMocks private ReadingQueryService service;

    /** A quiz whose questions must never be loaded on the list path. */
    private static class QuizWithUntouchableQuestions extends ReadingQuiz {
        @Override
        public List<ReadingQuestion> getQuestions() {
            throw new AssertionError("the template list walked quiz.getQuestions() -- that is the N+1");
        }
    }

    private static ReadingQuiz quiz(long id, String passage) {
        ReadingQuiz q = new QuizWithUntouchableQuestions();
        q.setQuizId(id);
        q.setTopic(Topic.SCIENCE);
        q.setDifficulty(Difficulty.PASSAGE_2);
        q.setPassageText(passage);
        q.setTimeLimitSeconds(1200);
        return q;
    }

    @Test
    @DisplayName("lists metadata, a passage preview and a question count without loading any question")
    void listDoesNotWalkQuestions() {
        String longPassage = "x".repeat(ReadingQueryService.TEMPLATE_PREVIEW_CHARS + 50);
        Page<ReadingQuiz> page = new PageImpl<>(List.of(quiz(1L, longPassage), quiz(2L, "short")),
                PageRequest.of(0, 20), 2);
        when(quizRepository.findQuizzesForAdmin(eq(Topic.SCIENCE), eq(null), eq("ADMIN"), any())).thenReturn(page);
        when(questionRepository.countByQuizIds(List.of(1L, 2L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 13L}));

        Page<ReadingQuizResponse> result = service.getTemplateList("SCIENCE", null, 0, 20);

        assertEquals(2, result.getTotalElements());
        ReadingQuizResponse first = result.getContent().get(0);
        assertEquals(13, first.getTotalQuestions());
        assertNull(first.getQuestions());
        assertEquals(ReadingQueryService.TEMPLATE_PREVIEW_CHARS + 1, first.getPassageText().length());
        assertTrue(first.getPassageText().endsWith("…"));
        // A quiz with no questions counted reads as 0, not as a missing figure.
        assertEquals(0, result.getContent().get(1).getTotalQuestions());
        assertEquals("short", result.getContent().get(1).getPassageText());
        verify(questionRepository, times(1)).countByQuizIds(any());
    }

    @Test
    @DisplayName("an empty page asks for no question counts at all")
    void emptyPageSkipsCountQuery() {
        when(quizRepository.findQuizzesForAdmin(any(), any(), eq("ADMIN"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 20), 0));

        Page<ReadingQuizResponse> result = service.getTemplateList(null, null, 3, 20);

        assertTrue(result.isEmpty());
        verify(questionRepository, never()).countByQuizIds(any());
    }

    @Test
    @DisplayName("page requests are clamped: negative page to 0, size to 1..100")
    void pageRequestClamp() {
        PageRequest tooBig = UserPageRequests.of(-4, 5000, Sort.by("createdAt"));
        assertEquals(0, tooBig.getPageNumber());
        assertEquals(UserPageRequests.MAX_SIZE, tooBig.getPageSize());

        PageRequest tooSmall = UserPageRequests.of(2, 0, Sort.unsorted());
        assertEquals(2, tooSmall.getPageNumber());
        assertEquals(1, tooSmall.getPageSize());
    }
}
