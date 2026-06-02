package com.smartprep.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.ReadingGenerateRequest;
import com.smartprep.dto.response.ReadingQuizResponse;
import com.smartprep.model.entity.*;
import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.QuestionType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.ReadingQuizRepository;
import com.smartprep.repository.ScoreHistoryRepository;
import com.smartprep.repository.UserRepository;
import com.smartprep.service.ai.GeminiClient;
import com.smartprep.service.ai.ReadingPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadingServiceTest {

    @Mock private ReadingQuizRepository quizRepository;
    @Mock private UserRepository userRepository;
    @Mock private ScoreHistoryRepository scoreHistoryRepository;
    @Mock private GeminiClient geminiClient;
    @Mock private ReadingPromptBuilder promptBuilder;

    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ReadingService readingService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .userId(1L)
                .username("teststudent")
                .build();
    }

    @Test
    @DisplayName("should parse structured options correctly during reading passage generation")
    void generateQuiz_success() {
        String mockAiResponse = """
                {
                  "passage": "This is paragraph A.\\n\\nThis is paragraph B.",
                  "questionGroups": [
                    {
                      "groupLabel": "Choose the correct letter A, B, C or D.",
                      "groupType": "MCQ",
                      "questions": [
                        {
                          "questionText": "What is the main theme?",
                          "options": [
                            { "label": "A", "content": "Technology in education" },
                            { "label": "B", "content": "Traditional learning" },
                            { "label": "C", "content": "Blended approaches" },
                            { "label": "D", "content": "None of the above" }
                          ],
                          "correctAnswer": "A",
                          "explanation": "Paragraph A states..."
                        }
                      ]
                    }
                  ]
                }
                """;

        ReadingGenerateRequest request = new ReadingGenerateRequest();
        request.setTopic("TECHNOLOGY");
        request.setDifficulty("PASSAGE_1");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptBuilder.buildSystemPrompt(any(Difficulty.class))).thenReturn("mock system prompt");
        when(promptBuilder.buildUserPrompt(any(Topic.class), any(Difficulty.class))).thenReturn("mock user prompt");
        when(geminiClient.generate(anyString(), anyString())).thenReturn(mockAiResponse);
        when(quizRepository.save(any(ReadingQuiz.class))).thenAnswer(invocation -> {
            ReadingQuiz quiz = invocation.getArgument(0);
            quiz.setQuizId(10L);
            if (quiz.getQuestions() != null) {
                long id = 100L;
                for (ReadingQuestion q : quiz.getQuestions()) {
                    q.setQuestionId(id++);
                    if (q.getOptions() != null) {
                        long optId = 1000L;
                        for (QuestionOption opt : q.getOptions()) {
                            opt.setOptionId(optId++);
                        }
                    }
                }
            }
            return quiz;
        });

        ReadingQuizResponse response = readingService.generateQuiz(1L, request);

        assertNotNull(response);
        assertEquals("TECHNOLOGY", response.getTopic());
        assertEquals("PASSAGE_1", response.getDifficulty());
        assertEquals(1, response.getQuestions().size());

        var qDto = response.getQuestions().get(0);
        assertEquals("What is the main theme?", qDto.getQuestionText());
        assertEquals("MCQ", qDto.getQuestionType());
        assertNotNull(qDto.getOptions());
        assertEquals(4, qDto.getOptions().size());
        assertEquals("A", qDto.getOptions().get(0).getLabel());
        assertEquals("Technology in education", qDto.getOptions().get(0).getContent());
    }
}
