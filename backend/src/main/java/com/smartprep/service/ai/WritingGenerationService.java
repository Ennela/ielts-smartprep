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
import com.smartprep.model.enums.WritingTaskType;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class WritingGenerationService {

    private final GeminiClient geminiClient;
    private final WritingPromptPersistence promptPersistence;
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

    /**
     * Deliberately not {@code @Transactional} -- see the note on
     * {@code ReadingGenerationService.generateQuiz}. The Gemini call in the middle of this
     * method used to run while holding a pooled database connection.
     *
     * <p>The two prompts are still written together: {@link WritingPromptPersistence} keeps
     * that one short transaction, so a failure on Task 2 cannot leave a stray Task 1 prompt
     * behind. It just no longer spans the network call.
     */
    public List<WritingPromptResponse> generatePromptPair(Long userId, WritingGenerateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // The learner's own choice; null ("Random / All Topics") lets the bank serve any prompt.
        Topic requestedTopic = null;
        if (request.getTopic() != null && !request.getTopic().isBlank()) {
            try {
                requestedTopic = Topic.valueOf(request.getTopic().toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                requestedTopic = Topic.EDUCATION; // fallback
            }
        }

        String difficulty = request.getDifficulty();
        String moduleType = request.getModuleType() != null ? request.getModuleType() : "ACADEMIC";

        // The bank goes first so Gemini quota is spent only once the learner has used it up.
        // Difficulty is not stored on a prompt, so it plays no part in the choice.
        List<WritingPrompt> banked = bankedPair(userId, requestedTopic, moduleType);
        if (banked != null) {
            return banked.stream().map(WritingGenerationService::toResponse).toList();
        }

        Topic topicVal;
        if (requestedTopic == null) {
            Topic[] topics = Topic.values();
            topicVal = topics[new Random().nextInt(topics.length)];
        } else {
            topicVal = requestedTopic;
        }

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
                .taskType(WritingTaskType.TASK_1)
                .visualData(t1VisualData)
                .topic(topicVal)
                .build();

        // Extract and map Task 2
        String t2Text = t2Node.path("promptText").asText("Task 2 essay prompt");
        String t2TypeStr = t2Node.path("essayType").asText("OPINION");
        EssayType t2Type = EssayType.valueOf(t2TypeStr.toUpperCase().trim());

        WritingPrompt t2Prompt = WritingPrompt.builder()
                .promptText(t2Text)
                .essayType(t2Type)
                .taskType(WritingTaskType.TASK_2)
                .topic(topicVal)
                .build();

        // Both rows in one short transaction, after the AI call rather than around it.
        List<WritingPrompt> saved = promptPersistence.savePair(t1Prompt, t2Prompt);
        t1Prompt = saved.get(0);
        t2Prompt = saved.get(1);

        return List.of(toResponse(t1Prompt), toResponse(t2Prompt));
    }

    private static WritingPromptResponse toResponse(WritingPrompt prompt) {
        return WritingPromptResponse.builder()
                .promptId(prompt.getPromptId())
                .promptText(prompt.getPromptText())
                .essayType(prompt.getEssayType().name())
                .taskType(prompt.getTaskType() != null ? prompt.getTaskType().name() : null)
                .imageUrl(prompt.getImageUrl())
                .visualData(prompt.getVisualData())
                .build();
    }

    // =========================================================================
    // Question bank
    // =========================================================================

    private static final List<EssayType> ACADEMIC_TASK1_TYPES = Arrays.stream(EssayType.values())
            .filter(t -> t.isTask1() && t != EssayType.LETTER).toList();
    private static final List<EssayType> TASK2_TYPES = Arrays.stream(EssayType.values())
            .filter(t -> !t.isTask1()).toList();

    /**
     * A Task 1 and a Task 2 prompt from the bank that the user has written neither essay for,
     * or null when it lacks either: the pair is then generated together, as before.
     */
    private List<WritingPrompt> bankedPair(Long userId, Topic topic, String moduleType) {
        List<EssayType> task1Types = "GENERAL_TRAINING".equalsIgnoreCase(moduleType)
                ? List.of(EssayType.LETTER) : ACADEMIC_TASK1_TYPES;
        Optional<WritingPrompt> task1 = unseen(WritingTaskType.TASK_1, task1Types, topic, userId);
        if (task1.isEmpty()) {
            return null;
        }
        Optional<WritingPrompt> task2 = unseen(WritingTaskType.TASK_2, TASK2_TYPES, topic, userId);
        if (task2.isEmpty()) {
            return null;
        }
        log.info("Serving bank WritingPrompt pair {} + {} to user: {}",
                task1.get().getPromptId(), task2.get().getPromptId(), userId);
        return List.of(task1.get(), task2.get());
    }

    private Optional<WritingPrompt> unseen(WritingTaskType taskType, List<EssayType> essayTypes, Topic topic, Long userId) {
        List<WritingPrompt> prompts = promptRepository.findUnseenPublished(
                taskType, essayTypes, topic, userId, PageRequest.of(0, 20));
        return prompts.isEmpty() ? Optional.empty() : Optional.of(prompts.get(new Random().nextInt(prompts.size())));
    }

    // =========================================================================
    // NotebookLM import
    // =========================================================================

    // NotebookLM's chat box is too short for the system prompt, so it goes into the notebook
    // once as a source, and each prompt is asked for with a short message that names it. The
    // output comes back through the admin import.

    /** The title the admin gives the format source in NotebookLM; the chat message names it. */
    public String notebookLmSourceTitle() {
        return "IELTS Writing format";
    }

    /**
     * The system prompt as a NotebookLM source. The other sources are style references only,
     * so the prompts stay original rather than copies of published tests.
     */
    public String buildNotebookLmInstructions() {
        return "# " + notebookLmSourceTitle() + "\n\n"
                + GENERATE_SYSTEM_PROMPT
                + "\nONE TASK ONLY:\n"
                + "- If the chat message asks for only Task 1 or only Task 2, return an object with only that key (\"task1\" or \"task2\").\n"
                + "\nVISUAL DATA (the app draws the chart from it):\n"
                + "- LINE_GRAPH, BAR_CHART: every row of \"data\" has the xAxisKey field and a number for every entry in \"keys\".\n"
                + "- PIE_CHART: every row has a \"name\" (the slice label) and a number under the first key, e.g. {\"name\": \"Housing\", \"value\": 35} with \"keys\": [\"value\"]. The values add up to 100.\n"
                + "- TABLE: \"data\" is an array of row objects with the same fields, the first field naming the row.\n"
                + "- MAP: \"map1\" and \"map2\" are arrays of short strings. DIAGRAM: \"steps\" is an array of strings.\n"
                + "- The figures must be realistic and change in ways a candidate can describe and compare.\n"
                + "\nNOTEBOOK SOURCES:\n"
                + "- Use the other sources in this notebook only as a reference for IELTS style, wording and difficulty.\n"
                + "- Do NOT copy prompts, charts or figures from the sources. Write a new, original prompt with new data.\n"
                + "- Do NOT add citation markers such as [1] anywhere in the output.\n";
    }

    /**
     * The short chat message asking for a prompt under the instructions above: the pair when no
     * essay type is given, otherwise only the task that type belongs to.
     */
    public String buildNotebookLmMessage(Topic topic, EssayType essayType) {
        String what;
        if (essayType == null) {
            what = "a new, original IELTS Writing prompt pair (task1 and task2). Module Type: ACADEMIC.";
        } else if (essayType.isTask1()) {
            what = String.format("only a new, original IELTS Writing Task 1 prompt (return only \"task1\"). essayType: %s. Module Type: %s.",
                    essayType, essayType == EssayType.LETTER ? "GENERAL_TRAINING" : "ACADEMIC");
        } else {
            what = String.format("only a new, original IELTS Writing Task 2 prompt (return only \"task2\"). essayType: %s.", essayType);
        }
        return String.format(
                "Follow every rule in the source \"%s\". Generate %s Topic: %s. "
                + "Return ONLY the JSON object, with no citation markers and no text before or after it.",
                notebookLmSourceTitle(), what, topic.name());
    }

    /**
     * Unsaved prompts parsed from ones written elsewhere (NotebookLM) in the format the Gemini
     * prompt asks for, either task or both. Code fences around the JSON are dropped; anything
     * malformed is the admin's input error, so it surfaces as an IllegalArgumentException (400).
     */
    public List<WritingPrompt> parseImportedPrompts(String content, Topic topic) {
        String json = content.trim()
                .replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw badImport("not valid JSON (" + e.getMessage() + ")");
        }
        if (root == null || !root.isObject()) {
            throw badImport("expected a JSON object with task1 and/or task2");
        }
        List<WritingPrompt> prompts = new ArrayList<>();
        if (root.hasNonNull("task1")) {
            prompts.add(importedPrompt(root.get("task1"), WritingTaskType.TASK_1, topic));
        }
        if (root.hasNonNull("task2")) {
            prompts.add(importedPrompt(root.get("task2"), WritingTaskType.TASK_2, topic));
        }
        if (prompts.isEmpty()) {
            throw badImport("expected a JSON object with task1 and/or task2");
        }
        return prompts;
    }

    private static WritingPrompt importedPrompt(JsonNode node, WritingTaskType taskType, Topic topic) {
        String key = taskType == WritingTaskType.TASK_1 ? "task1" : "task2";
        String text = node.path("promptText").asText("").trim();
        if (text.isEmpty()) {
            throw badImport(key + ".promptText is missing");
        }
        String typeStr = node.path("essayType").asText("").trim().toUpperCase();
        EssayType type;
        try {
            type = EssayType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw badImport(key + ".essayType \"" + typeStr + "\" is not an essay type");
        }
        if (type.isTask1() != (taskType == WritingTaskType.TASK_1)) {
            throw badImport(key + ".essayType " + type + " does not belong to " + key);
        }
        String visualData = null;
        if (type.isTask1() && type != EssayType.LETTER) {
            JsonNode visual = node.path("visualData");
            checkVisualData(type, visual);
            visualData = visual.toString();
        }
        return WritingPrompt.builder()
                .promptText(text)
                .essayType(type)
                .taskType(taskType)
                .visualData(visualData)
                .topic(topic)
                .build();
    }

    /** What VisualDataRenderer needs to draw the chart, so an import cannot save a blank one. */
    private static void checkVisualData(EssayType type, JsonNode visual) {
        if (!visual.isObject()) {
            throw badImport("task1.visualData must be a JSON object for " + type);
        }
        switch (type) {
            case MAP -> {
                requireTextList(visual, "map1");
                requireTextList(visual, "map2");
            }
            case DIAGRAM -> requireTextList(visual, "steps");
            default -> {
                JsonNode data = visual.path("data");
                if (!data.isArray() || data.isEmpty()) {
                    throw badImport("task1.visualData.data must be a non-empty array of rows");
                }
                for (JsonNode row : data) {
                    if (!row.isObject()) {
                        throw badImport("each row of task1.visualData.data must be an object");
                    }
                }
                if (type == EssayType.TABLE) {
                    return;
                }
                JsonNode keys = visual.path("keys");
                if (!keys.isArray() || keys.isEmpty()) {
                    throw badImport("task1.visualData.keys must list the data series");
                }
                // A pie labels its slices by "name"; the axis charts by xAxisKey.
                String labelKey = type == EssayType.PIE_CHART ? "name" : visual.path("xAxisKey").asText("name");
                for (JsonNode row : data) {
                    if (!row.hasNonNull(labelKey)) {
                        throw badImport("a row of task1.visualData.data has no \"" + labelKey + "\"");
                    }
                    for (JsonNode k : keys) {
                        if (!row.path(k.asText()).isNumber()) {
                            throw badImport("a row of task1.visualData.data has no number for \"" + k.asText() + "\"");
                        }
                    }
                }
            }
        }
    }

    private static void requireTextList(JsonNode visual, String field) {
        JsonNode list = visual.path(field);
        boolean ok = list.isArray() && !list.isEmpty();
        for (JsonNode item : list) {
            ok &= item.isTextual();
        }
        if (!ok) {
            throw badImport("task1.visualData." + field + " must be a non-empty array of strings");
        }
    }

    private static IllegalArgumentException badImport(String reason) {
        return new IllegalArgumentException("Cannot import these prompts: " + reason);
    }
}
