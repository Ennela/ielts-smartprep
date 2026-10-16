package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.AdminWritingImportRequest;
import com.smartprep.dto.request.WritingGenerateRequest;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.Topic;
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import com.smartprep.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** The Writing bank: published prompts served before Gemini, and prompts imported from NotebookLM. */
@ExtendWith(MockitoExtension.class)
class WritingQuestionBankTest {

    @Mock private GeminiClient geminiClient;
    @Mock private WritingPromptPersistence promptPersistence;
    @Mock private WritingPromptRepository promptRepository;
    @Mock private UserRepository userRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private WritingGenerationService service;

    private static final String BAR_CHART_TASK1 = """
            {"promptText": "The chart below shows commuting by bike in three cities.", "essayType": "BAR_CHART",
             "visualData": {"title": "Cycling to work", "xAxisLabel": "City", "yAxisLabel": "%", "xAxisKey": "city",
                            "keys": ["2000", "2020"],
                            "data": [{"city": "Oslo", "2000": 8, "2020": 21}, {"city": "Lyon", "2000": 5, "2020": 14}]}}
            """;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().userId(1L).username("learner").build();
    }

    private static WritingPrompt prompt(long id, EssayType type) {
        return WritingPrompt.builder().promptId(id).promptText("Prompt " + id).essayType(type)
                .taskType(type.isTask1() ? WritingTaskType.TASK_1 : WritingTaskType.TASK_2).build();
    }

    private static WritingGenerateRequest request(String topic, String moduleType) {
        WritingGenerateRequest request = new WritingGenerateRequest();
        request.setTopic(topic);
        request.setDifficulty("MEDIUM");
        request.setModuleType(moduleType);
        return request;
    }

    @Test
    @DisplayName("an Academic pair comes from the bank, without calling Gemini, when both tasks have an unseen prompt on the topic")
    void pair_servedFromBankFirst() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_1), any(), eq(Topic.HEALTH), eq(1L), any()))
                .thenReturn(List.of(prompt(11L, EssayType.MAP)));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_2), any(), eq(Topic.HEALTH), eq(1L), any()))
                .thenReturn(List.of(prompt(12L, EssayType.OPINION)));

        List<WritingPromptResponse> pair = service.generatePromptPair(1L, request("health", "ACADEMIC"));

        assertEquals(List.of(11L, 12L), pair.stream().map(WritingPromptResponse::getPromptId).toList());
        verifyNoInteractions(geminiClient, promptPersistence);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EssayType>> types = ArgumentCaptor.forClass(Collection.class);
        verify(promptRepository).findUnseenPublished(eq(WritingTaskType.TASK_1), types.capture(), any(), any(), any());
        assertFalse(types.getValue().contains(EssayType.LETTER));
        assertTrue(types.getValue().containsAll(List.of(EssayType.LINE_GRAPH, EssayType.MAP, EssayType.DIAGRAM)));
    }

    @Test
    @DisplayName("General Training draws a letter for Task 1, and \"any topic\" searches every prompt, untagged ones included")
    void pair_generalTrainingAnyTopic() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_1), eq(List.of(EssayType.LETTER)), isNull(), eq(1L), any()))
                .thenReturn(List.of(prompt(21L, EssayType.LETTER)));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_2), any(), isNull(), eq(1L), any()))
                .thenReturn(List.of(prompt(22L, EssayType.DISCUSSION)));

        List<WritingPromptResponse> pair = service.generatePromptPair(1L, request("", "GENERAL_TRAINING"));

        assertEquals(List.of(21L, 22L), pair.stream().map(WritingPromptResponse::getPromptId).toList());
        verifyNoInteractions(geminiClient);
    }

    @Test
    @DisplayName("the pair goes to Gemini when the bank lacks either task, and the new prompts keep their topic")
    void pair_geminiWhenATaskIsMissing() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_1), any(), eq(Topic.HISTORY), eq(1L), any()))
                .thenReturn(List.of(prompt(31L, EssayType.TABLE)));
        when(promptRepository.findUnseenPublished(eq(WritingTaskType.TASK_2), any(), eq(Topic.HISTORY), eq(1L), any()))
                .thenReturn(List.of());
        String gemini = "{\"task1\": " + BAR_CHART_TASK1 + ", \"task2\": {\"promptText\": \"Discuss.\", \"essayType\": \"DISCUSSION\"}}";
        when(geminiClient.generateAndParse(anyString(), anyString(), any()))
                .thenAnswer(inv -> ((GeminiClient.CheckedFunction<String, ?>) inv.getArgument(2)).apply(gemini));
        when(promptPersistence.savePair(any(), any())).thenAnswer(inv -> List.of(inv.getArgument(0), inv.getArgument(1)));

        service.generatePromptPair(1L, request("HISTORY", "ACADEMIC"));

        ArgumentCaptor<WritingPrompt> t1 = ArgumentCaptor.forClass(WritingPrompt.class);
        ArgumentCaptor<WritingPrompt> t2 = ArgumentCaptor.forClass(WritingPrompt.class);
        verify(promptPersistence).savePair(t1.capture(), t2.capture());
        assertEquals(Topic.HISTORY, t1.getValue().getTopic());
        assertEquals(Topic.HISTORY, t2.getValue().getTopic());
        assertEquals(EssayType.BAR_CHART, t1.getValue().getEssayType());
    }

    @Test
    @DisplayName("an import may hold only Task 1, code fences dropped, with its chart data kept for the renderer")
    void import_task1OnlyFenced() {
        List<WritingPrompt> prompts = service.parseImportedPrompts(
                "```json\n{\"task1\": " + BAR_CHART_TASK1 + "}\n```", Topic.ENVIRONMENT);

        assertEquals(1, prompts.size());
        WritingPrompt p = prompts.get(0);
        assertEquals(WritingTaskType.TASK_1, p.getTaskType());
        assertEquals(EssayType.BAR_CHART, p.getEssayType());
        assertEquals(Topic.ENVIRONMENT, p.getTopic());
        assertTrue(p.getVisualData().contains("\"Oslo\""));
    }

    @Test
    @DisplayName("an import that is not the expected JSON, or a chart the app cannot draw, is a bad request")
    void import_rejectsMalformedContent() {
        assertBadImport("Here are your prompts", "not valid JSON");
        assertBadImport("{\"essay\": \"x\"}", "task1 and/or task2");
        assertBadImport("{\"task2\": {\"promptText\": \"x\", \"essayType\": \"ESSAY\"}}", "not an essay type");
        assertBadImport("{\"task2\": {\"promptText\": \"x\", \"essayType\": \"MAP\"}}", "does not belong to task2");
        assertBadImport("{\"task1\": {\"promptText\": \"x\", \"essayType\": \"LINE_GRAPH\", \"visualData\": \"a chart\"}}",
                "must be a JSON object");
        assertBadImport("{\"task1\": {\"promptText\": \"x\", \"essayType\": \"PIE_CHART\", \"visualData\": "
                + "{\"keys\": [\"value\"], \"data\": [{\"item\": \"Rent\", \"value\": 40}]}}}", "has no \"name\"");
        assertBadImport("{\"task1\": {\"promptText\": \"x\", \"essayType\": \"MAP\", \"visualData\": "
                + "{\"map1\": [{\"x\": 1}], \"map2\": [\"park\"]}}}", "map1 must be a non-empty array of strings");
    }

    private void assertBadImport(String content, String reason) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.parseImportedPrompts(content, Topic.HEALTH));
        assertTrue(ex.getMessage().startsWith("Cannot import these prompts"), ex.getMessage());
        assertTrue(ex.getMessage().contains(reason), ex.getMessage());
    }

    @Test
    @DisplayName("imported prompts are saved awaiting review with their topic, so learners do not get them yet")
    void import_savedAwaitingReview() {
        AdminService adminService = new AdminService(null, null, promptRepository, null, null, null, null, null, service);
        when(promptRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<WritingPrompt> saved = adminService.importWritingPrompts(AdminWritingImportRequest.builder()
                .topic("technology")
                .content("{\"task1\": {\"promptText\": \"Write to your landlord.\", \"essayType\": \"letter\", \"visualData\": null},"
                        + " \"task2\": {\"promptText\": \"Some people think...\", \"essayType\": \"OPINION\"}}")
                .build());

        assertEquals(2, saved.size());
        for (WritingPrompt p : saved) {
            assertEquals(ContentStatus.AI_IMPORTED, p.getContentStatus());
            assertEquals("NOTEBOOKLM", p.getSource());
            assertEquals(Topic.TECHNOLOGY, p.getTopic());
            assertNotNull(p.getImportedAt());
        }
        assertEquals(EssayType.LETTER, saved.get(0).getEssayType());
        assertNull(saved.get(0).getVisualData());
    }

    @Test
    @DisplayName("the NotebookLM chat message is short, names the format source, and asks for one task when given a type")
    void notebookLm_messageIsShortAndNamesTheSource() {
        String title = service.notebookLmSourceTitle();
        assertTrue(service.buildNotebookLmInstructions().startsWith("# " + title));
        assertTrue(service.buildNotebookLmInstructions().contains("\"task1\""));

        for (EssayType type : new EssayType[]{null, EssayType.PIE_CHART, EssayType.LETTER, EssayType.OPINION}) {
            String message = service.buildNotebookLmMessage(Topic.SCIENCE, type);
            assertTrue(message.contains("\"" + title + "\""));
            assertTrue(message.contains("SCIENCE"));
            assertTrue(message.length() < 500, "chat message is " + message.length() + " chars");
        }
        assertTrue(service.buildNotebookLmMessage(Topic.SCIENCE, EssayType.LETTER).contains("GENERAL_TRAINING"));
        assertTrue(service.buildNotebookLmMessage(Topic.SCIENCE, EssayType.OPINION).contains("only \"task2\""));
    }
}
