package com.smartprep.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.request.WritingGenerateRequest;
import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import com.smartprep.model.enums.Topic;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingGenerationService {

    private final GeminiClient geminiClient;
    private final WritingPromptRepository promptRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final String GENERATE_SYSTEM_PROMPT = """
            You are an expert IELTS Writing exam designer with 15 years of experience.
            Your job is to generate a paired IELTS Writing mock test containing:
            1. Writing Task 1:
               - For ACADEMIC module: Generate a report topic describing visual data. The essayType must be one of: LINE_GRAPH, BAR_CHART, PIE_CHART, TABLE, MAP, DIAGRAM.
                 - For LINE_GRAPH, BAR_CHART, PIE_CHART, or TABLE:
                   - The visualData field must be a JSON object containing:
                     - "title": Title of chart
                     - "xAxisLabel": e.g. "Year" or "Category"
                     - "yAxisLabel": e.g. "Percentage (%)" or "Value"
                     - "xAxisKey": e.g. "year" or "item"
                     - "keys": array of keys representing different columns/lines (e.g. ["Male", "Female"])
                     - "data": array of data objects suitable for Recharts (e.g. [{"year": "1990", "Male": 20, "Female": 30}, ...])
                 - For MAP:
                   - The visualData field must be a JSON object containing:
                     - "title": Map title
                     - "map1": list of points/features for the first time period/location
                     - "map2": list of points/features for the second time period/location
                 - For DIAGRAM:
                   - The visualData field must be a JSON object containing:
                     - "title": Process title
                     - "steps": array of strings listing step-by-step stages of the process
               - For GENERAL_TRAINING module: Generate a scenario requiring a formal, semi-formal, or informal letter.
                 - essayType must be "LETTER"
                 - visualData must be null
            2. Writing Task 2:
               - Generate an essay prompt based on the chosen topic and difficulty.
               - essayType must be one of: OPINION, DISCUSSION, CAUSE_AND_EFFECT, PROBLEM_AND_SOLUTION, ADVANTAGES_DISADVANTAGES, TWO_PART_QUESTION.

            You MUST return a valid JSON object matching this structure:
            {
              "task1": {
                "promptText": "The chart below shows...",
                "essayType": "BAR_CHART", // or LINE_GRAPH, PIE_CHART, TABLE, MAP, DIAGRAM, LETTER
                "visualData": { ... } // object or null
              },
              "task2": {
                "promptText": "Some people believe that...",
                "essayType": "OPINION"
              }
            }

            The topics and vocabulary used in the prompts should match the requested difficulty (EASY, MEDIUM, HARD).
            Ensure no extra text, no markdown fences, and only valid JSON is returned.
            """;

    @Transactional
    public List<WritingPromptResponse> generatePromptPair(Long userId, WritingGenerateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Topic topicVal;
        if (request.getTopic() == null || request.getTopic().isBlank()) {
            Topic[] topics = Topic.values();
            topicVal = topics[new Random().nextInt(topics.length)];
        } else {
            try {
                topicVal = Topic.valueOf(request.getTopic().toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                topicVal = Topic.EDUCATION; // fallback
            }
        }

        String difficulty = request.getDifficulty();
        String moduleType = request.getModuleType() != null ? request.getModuleType() : "ACADEMIC";

        // Build user instructions
        String userPrompt = String.format(
            "Please generate an IELTS Writing prompt pair.\nTopic: %s\nDifficulty: %s\nModule Type: %s",
            topicVal.name(), difficulty, moduleType
        );

        JsonNode responseJson = geminiClient.generateAndParse(
                GENERATE_SYSTEM_PROMPT,
                userPrompt,
                aiResponse -> {
                    JsonNode json = objectMapper.readTree(aiResponse);
                    if (!json.has("task1") || !json.has("task2")) {
                        throw new InvalidAiResponseException("AI response missing task1 or task2 nodes");
                    }
                    return json;
                }
        );

        JsonNode t1Node = responseJson.path("task1");
        JsonNode t2Node = responseJson.path("task2");

        // Extract and map Task 1
        String t1Text = t1Node.path("promptText").asText("Task 1 description");
        String t1TypeStr = t1Node.path("essayType").asText("BAR_CHART");
        EssayType t1Type = EssayType.valueOf(t1TypeStr.toUpperCase().trim());
        String t1VisualData = t1Node.has("visualData") && !t1Node.path("visualData").isNull()
                ? t1Node.path("visualData").toString()
                : null;

        WritingPrompt t1Prompt = WritingPrompt.builder()
                .promptText(t1Text)
                .essayType(t1Type)
                .visualData(t1VisualData)
                .build();
        t1Prompt = promptRepository.save(t1Prompt);

        // Extract and map Task 2
        String t2Text = t2Node.path("promptText").asText("Task 2 essay prompt");
        String t2TypeStr = t2Node.path("essayType").asText("OPINION");
        EssayType t2Type = EssayType.valueOf(t2TypeStr.toUpperCase().trim());

        WritingPrompt t2Prompt = WritingPrompt.builder()
                .promptText(t2Text)
                .essayType(t2Type)
                .build();
        t2Prompt = promptRepository.save(t2Prompt);

        return List.of(
                WritingPromptResponse.builder()
                        .promptId(t1Prompt.getPromptId())
                        .promptText(t1Prompt.getPromptText())
                        .essayType(t1Prompt.getEssayType().name())
                        .imageUrl(t1Prompt.getImageUrl())
                        .visualData(t1Prompt.getVisualData())
                        .build(),
                WritingPromptResponse.builder()
                        .promptId(t2Prompt.getPromptId())
                        .promptText(t2Prompt.getPromptText())
                        .essayType(t2Prompt.getEssayType().name())
                        .imageUrl(t2Prompt.getImageUrl())
                        .visualData(t2Prompt.getVisualData())
                        .build()
        );
    }
}
