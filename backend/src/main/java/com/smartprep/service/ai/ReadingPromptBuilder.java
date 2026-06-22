package com.smartprep.service.ai;

import com.smartprep.model.enums.Difficulty;
import com.smartprep.model.enums.Topic;
import org.springframework.stereotype.Component;

@Component
public class ReadingPromptBuilder {

    /**
     * Build the system prompt based on difficulty level.
     * Each level has different vocabulary complexity, sentence structure, and question types.
     */
    public String buildSystemPrompt(Difficulty difficulty) {
        return switch (difficulty) {
            case PASSAGE_1 -> SYSTEM_PROMPT_PASSAGE_1;
            case PASSAGE_2 -> SYSTEM_PROMPT_PASSAGE_2;
            case PASSAGE_3 -> SYSTEM_PROMPT_PASSAGE_3;
        };
    }

    /**
     * Build the user prompt with the specific topic.
     */
    public String buildUserPrompt(Topic topic, Difficulty difficulty) {
        return buildUserPrompt(topic, difficulty, null);
    }

    /**
     * Build the user prompt with the specific topic and focus question type.
     */
    public String buildUserPrompt(Topic topic, Difficulty difficulty, String focusQuestionType) {
        String topicLabel = formatTopic(topic);
        String difficultyLabel = formatDifficulty(difficulty);
        String focusInstruction = "";
        if (focusQuestionType != null && !focusQuestionType.isBlank()) {
            focusInstruction = String.format(" Please ensure that one of the question groups specifically focuses on the question type: %s, making it particularly detailed and well-crafted to test this skill.", focusQuestionType);
        }
        return String.format(
                "Generate an IELTS Academic Reading passage about the topic: \"%s\". "
                + "Difficulty level: %s.%s "
                + "Follow all the rules in the system prompt strictly. "
                + "Return ONLY the JSON object, no extra text.",
                topicLabel, difficultyLabel, focusInstruction
        );
    }

    private String formatTopic(Topic topic) {
        return switch (topic) {
            case ENVIRONMENT -> "Environment and Climate Change";
            case TECHNOLOGY -> "Technology and Innovation";
            case HISTORY -> "History and Civilization";
            case HEALTH -> "Health and Medicine";
            case EDUCATION -> "Education and Learning";
            case SCIENCE -> "Science and Research";
            case SOCIETY -> "Society and Social Studies";
        };
    }

    private String formatDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case PASSAGE_1 -> "Passage 1 (Easy)";
            case PASSAGE_2 -> "Passage 2 (Medium)";
            case PASSAGE_3 -> "Passage 3 (Hard)";
        };
    }

    // =========================================================================
    // SYSTEM PROMPTS - 3 Levels with questionGroups format
    // =========================================================================

    private static final String SYSTEM_PROMPT_PASSAGE_1 = """
            You are an IELTS Academic Reading test generator. Generate a reading passage and questions at PASSAGE 1 (Easy) level.

            PASSAGE REQUIREMENTS:
            - Length: 600-900 words.
            - Topic: as specified by the user.
            - Vocabulary: simple to intermediate (CEFR A2-B1). Common everyday words.
            - Sentence structure: short, simple sentences. Minimal complex clauses.
            - Content: factual, descriptive. Clear, linear presentation.
            - MUST label paragraphs: A, B, C, D, E... (each paragraph starts with its letter label followed by a period and a space).
              Example: "A. The first paragraph text...\\n\\nB. The second paragraph text..."
            - 5-6 paragraphs.
            - EVIDENCE MARKER INSTRUCTION: In the generated `passage` string, you MUST wrap the exact sentence or phrase that provides the evidence for each question's answer in `[ANS_X]...[/ANS_X]` tags, where X is the sequential question number (from 1 to 13). Every question from 1 to 13 MUST have its evidence segment wrapped in `[ANS_X]...[/ANS_X]` in the passage.

            QUESTION REQUIREMENTS:
            - Generate exactly 13 questions total.
            - Organize into 3 question groups with this mix:
              * Group 1: TFNG (True/False/Not Given) — 4 to 5 questions
              * Group 2: MCQ (Multiple Choice) — 3 to 4 questions
              * Group 3: SENTENCE_COMPLETION — 3 to 4 questions (NO MORE THAN THREE WORDS from the passage)
            - Total must be exactly 13 questions.

            QUESTION TYPE RULES:
            - TFNG: correctAnswer must be exactly "TRUE", "FALSE", or "NOT GIVEN". Include at least one of each.
            - MCQ: 4 options (A, B, C, D). correctAnswer is the letter only (e.g. "A").
            - SENTENCE_COMPLETION: Student fills in words from the passage. correctAnswer is the exact word(s). Set wordLimit to 3.

            OUTPUT FORMAT (strict JSON):
            {
              "passage": "A. First paragraph...\\n\\nB. Second paragraph...",
              "questionGroups": [
                {
                  "groupLabel": "Questions 1-5: Do the following statements agree with the information given in the passage?",
                  "groupType": "TFNG",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "Statement to evaluate.",
                      "correctAnswer": "TRUE",
                      "explanation": "Detailed explanation referencing the passage."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 6-9: Choose the correct letter A, B, C or D.",
                  "groupType": "MCQ",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "What is the main purpose of...?",
                      "options": [
                        { "label": "A", "content": "option text" },
                        { "label": "B", "content": "option text" },
                        { "label": "C", "content": "option text" },
                        { "label": "D", "content": "option text" }
                      ],
                      "correctAnswer": "B",
                      "explanation": "Detailed explanation."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 10-13: Complete the sentences below. Choose NO MORE THAN THREE WORDS from the passage.",
                  "groupType": "SENTENCE_COMPLETION",
                  "groupContext": null,
                  "wordLimit": 3,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "The main cause of pollution is ___.",
                      "correctAnswer": "industrial waste",
                      "explanation": "In paragraph C, the passage states..."
                    }
                  ]
                }
              ]
            }

            IMPORTANT:
            - Return ONLY valid JSON. No markdown, no code fences, no extra text.
            - The passage must be original. All answers must be unambiguously correct.
            - For SENTENCE_COMPLETION, the answer MUST appear verbatim in the passage.
            - Ensure question numbering in groupLabel is sequential across all groups.
            """;

    private static final String SYSTEM_PROMPT_PASSAGE_2 = """
            You are an IELTS Academic Reading test generator. Generate a reading passage and questions at PASSAGE 2 (Medium) level.

            PASSAGE REQUIREMENTS:
            - Length: 600-900 words.
            - Topic: as specified by the user.
            - Vocabulary: intermediate to upper-intermediate (CEFR B1-B2). Include academic vocabulary.
            - Sentence structure: mix of simple and complex sentences with subordinate clauses, relative clauses, passive voice.
            - Content: analytical and argumentative. Multiple viewpoints or cause-effect relationships.
            - MUST label paragraphs: A, B, C, D, E, F... (each paragraph starts with its letter label).
            - 6-7 paragraphs.
            - EVIDENCE MARKER INSTRUCTION: In the generated `passage` string, you MUST wrap the exact sentence or phrase that provides the evidence for each question's answer in `[ANS_X]...[/ANS_X]` tags, where X is the sequential question number (from 1 to 13). Every question from 1 to 13 MUST have its evidence segment wrapped in `[ANS_X]...[/ANS_X]` in the passage.

            QUESTION REQUIREMENTS:
            - Generate exactly 13 questions total.
            - Organize into 3-4 question groups with this mix:
              * Group 1: YNNG (Yes/No/Not Given) — 3 to 4 questions
              * Group 2: MATCHING_INFORMATION — 3 to 4 questions (match statements to paragraphs)
              * Group 3: SUMMARY_COMPLETION — 3 to 4 questions (fill blanks in a summary)
              * Group 4: MCQ — 2 to 3 questions
            - Total must be exactly 13 questions.

            QUESTION TYPE RULES:
            - YNNG: correctAnswer must be exactly "YES", "NO", or "NOT GIVEN". Include at least one of each.
            - MATCHING_INFORMATION: Match each statement to the correct paragraph (A, B, C...). Provide all paragraph letters as options.
            - SUMMARY_COMPLETION: Provide a summary paragraph with numbered blanks (___9___, ___10___). Student fills words from the passage OR chooses from a word bank. If using a word bank, provide "options" array with the word choices. Set wordLimit appropriately.
            - MCQ: 4 options (A, B, C, D). correctAnswer is the letter only.

            OUTPUT FORMAT (strict JSON):
            {
              "passage": "A. First paragraph...\\n\\nB. Second paragraph...",
              "questionGroups": [
                {
                  "groupLabel": "Questions 1-4: Do the following statements agree with the claims of the writer?",
                  "groupType": "YNNG",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "The writer believes that...",
                      "correctAnswer": "YES",
                      "explanation": "Detailed explanation."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 5-8: Which paragraph contains the following information?",
                  "groupType": "MATCHING_INFORMATION",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": ["A", "B", "C", "D", "E", "F"],
                  "questions": [
                    {
                      "questionText": "A description of how the technology was first developed",
                      "correctAnswer": "C",
                      "explanation": "Paragraph C discusses..."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 9-11: Complete the summary below. Choose NO MORE THAN TWO WORDS from the passage.",
                  "groupType": "SUMMARY_COMPLETION",
                  "groupContext": "The study found that ___9___ played a significant role in determining outcomes. Researchers also noted that ___10___ could reduce the negative effects. The final conclusion emphasized the importance of ___11___.",
                  "wordLimit": 2,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "Blank 9",
                      "correctAnswer": "genetic factors",
                      "explanation": "In paragraph D..."
                    },
                    {
                      "questionText": "Blank 10",
                      "correctAnswer": "early intervention",
                      "explanation": "In paragraph E..."
                    },
                    {
                      "questionText": "Blank 11",
                      "correctAnswer": "preventive measures",
                      "explanation": "In paragraph F..."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 12-13: Choose the correct letter A, B, C or D.",
                  "groupType": "MCQ",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": null,
                  "questions": [
                    {
                      "questionText": "What is the writer's main argument?",
                      "options": [
                        { "label": "A", "content": "..." },
                        { "label": "B", "content": "..." },
                        { "label": "C", "content": "..." },
                        { "label": "D", "content": "..." }
                      ],
                      "correctAnswer": "C",
                      "explanation": "Detailed explanation."
                    }
                  ]
                }
              ]
            }

            IMPORTANT ABOUT SUMMARY_COMPLETION:
            - You may create TWO variants:
              1. "Fill from passage" (no options array, student types words) — set options to null
              2. "Choose from word bank" (provide options array with 6-8 word choices, more than the number of blanks) — set options to an array of strings
            - You can use EITHER variant or BOTH in the same passage. Mix them for variety.

            IMPORTANT:
            - Return ONLY valid JSON. No markdown, no code fences, no extra text.
            - The passage must be original. All answers must be unambiguously correct.
            - NOT GIVEN means the information is neither confirmed nor denied.
            - Ensure question numbering in groupLabel is sequential.
            """;

    private static final String SYSTEM_PROMPT_PASSAGE_3 = """
            You are an IELTS Academic Reading test generator. Generate a reading passage and questions at PASSAGE 3 (Hard) level.

            PASSAGE REQUIREMENTS:
            - Length: 600-900 words.
            - Topic: as specified by the user.
            - Vocabulary: advanced academic (CEFR B2-C1). Discipline-specific terminology.
            - Sentence structure: complex and compound-complex. Nominalization, hedging, academic discourse markers.
            - Content: critically analytical. Nuanced arguments, counterarguments, evidence-based reasoning.
            - MUST label paragraphs: A, B, C, D, E, F, G... (each paragraph starts with its letter label).
            - 7-8 paragraphs.
            - EVIDENCE MARKER INSTRUCTION: In the generated `passage` string, you MUST wrap the exact sentence or phrase that provides the evidence for each question's answer in `[ANS_X]...[/ANS_X]` tags, where X is the sequential question number (from 1 to 13). Every question from 1 to 13 MUST have its evidence segment wrapped in `[ANS_X]...[/ANS_X]` in the passage.

            QUESTION REQUIREMENTS:
            - Generate exactly 13 questions total.
            - Organize into 3 question groups with this mix:
              * Group 1: MATCHING_HEADINGS — 5 to 7 questions (match headings to paragraphs)
              * Group 2: MATCHING_FEATURES — 3 to 4 questions (match statements to people/theories/entities)
              * Group 3: MATCHING_SENTENCE_ENDINGS — 3 to 4 questions (match sentence beginnings to endings)
            - Total must be exactly 13 questions.

            QUESTION TYPE RULES:
            - MATCHING_HEADINGS: Provide a list of headings (more headings than paragraphs, using roman numerals: i, ii, iii...). Student matches each paragraph to a heading. correctAnswer is the roman numeral.
            - MATCHING_FEATURES: Provide a list of people/theories/entities (A, B, C...) as options. Match statements to the correct entity. correctAnswer is the letter.
            - MATCHING_SENTENCE_ENDINGS: Provide a list of sentence endings (A, B, C, D...) as options. Student matches sentence beginnings to correct endings. correctAnswer is the letter.

            OUTPUT FORMAT (strict JSON):
            {
              "passage": "A. First paragraph...\\n\\nB. Second paragraph...",
              "questionGroups": [
                {
                  "groupLabel": "Questions 1-7: The passage has seven paragraphs, A-G. Choose the correct heading for each paragraph.",
                  "groupType": "MATCHING_HEADINGS",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": ["i. The role of technology in modern education", "ii. Historical perspectives on learning", "iii. Challenges facing educators today", "iv. The impact of standardized testing", "v. Future directions in pedagogy", "vi. Student motivation and engagement", "vii. The digital divide in education", "viii. Collaborative learning approaches", "ix. Assessment reform proposals"],
                  "questions": [
                    {
                      "questionText": "Paragraph A",
                      "correctAnswer": "ii",
                      "explanation": "Paragraph A discusses the historical context of..."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 8-10: Look at the following statements. Match each statement with the correct researcher A-D.",
                  "groupType": "MATCHING_FEATURES",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": ["A. Dr. Smith", "B. Professor Johnson", "C. Dr. Williams", "D. Professor Brown"],
                  "questions": [
                    {
                      "questionText": "argued that traditional methods remain effective",
                      "correctAnswer": "B",
                      "explanation": "In paragraph D, Professor Johnson states..."
                    }
                  ]
                },
                {
                  "groupLabel": "Questions 11-13: Complete each sentence with the correct ending A-F.",
                  "groupType": "MATCHING_SENTENCE_ENDINGS",
                  "groupContext": null,
                  "wordLimit": null,
                  "options": ["A. has led to improved outcomes.", "B. remains a subject of debate.", "C. was rejected by later researchers.", "D. requires significant investment.", "E. contradicts earlier findings.", "F. supports the original hypothesis."],
                  "questions": [
                    {
                      "questionText": "The implementation of the new curriculum",
                      "correctAnswer": "D",
                      "explanation": "In paragraph F..."
                    }
                  ]
                }
              ]
            }

            IMPORTANT:
            - Return ONLY valid JSON. No markdown, no code fences, no extra text.
            - The passage must be original. All answers must be unambiguously correct.
            - For MATCHING_HEADINGS, provide MORE headings than paragraphs (at least 2 extra distractors).
            - For MATCHING_SENTENCE_ENDINGS, provide MORE endings than questions (at least 2 extra distractors).
            - Ensure question numbering in groupLabel is sequential.
            """;

    public String buildSystemPromptFull(String moduleType) {
        if ("GENERAL_TRAINING".equalsIgnoreCase(moduleType)) {
            return SYSTEM_PROMPT_FULL_TEST_GT;
        }
        return SYSTEM_PROMPT_FULL_TEST_ACADEMIC;
    }

    public String buildUserPromptFull(Topic topic, String moduleType) {
        String topicLabel = formatTopic(topic);
        return String.format(
                "Generate a full IELTS %s Reading Mock Test (3 passages/sections, exactly 40 questions total) on the general theme of \"%s\". "
                + "Follow all instructions and JSON structure rules in the system prompt. "
                + "Return ONLY the JSON object, no other text.",
                "GENERAL_TRAINING".equalsIgnoreCase(moduleType) ? "General Training" : "Academic",
                topicLabel
        );
    }

    private static final String SYSTEM_PROMPT_FULL_TEST_ACADEMIC = """
            You are an IELTS Academic Reading test generator. Generate a full Academic Reading mock test consisting of exactly 3 passages and exactly 40 questions total.

            STRUCTURE & DIFFICULTY:
            - Passage 1: Easy (600-800 words), everyday/general academic topics, simple grammar. Exactly 13 questions (Q1 to Q13).
            - Passage 2: Medium (700-900 words), more academic/analytical, compound sentences. Exactly 13 questions (Q14 to Q26).
            - Passage 3: Hard (800-1000 words), complex scientific or technical topics, formal/advanced language. Exactly 14 questions (Q27 to Q40).
            - Total questions: Exactly 40. No more, no less.
            - Must label paragraphs in each passage: A, B, C, D, E... (each paragraph starts with its letter label followed by a period and a space, e.g., "A. Paragraph text...").

            QUESTION TYPES (Choose a mix of 4-5 different types across the test from these 11 official types):
            1. MCQ (Multiple choice): Options label A, B, C, D in questions.
            2. TFNG (True/False/Not Given): correctAnswer is "TRUE", "FALSE", or "NOT GIVEN".
            3. YNNG (Yes/No/Not Given): correctAnswer is "YES", "NO", or "NOT GIVEN".
            4. MATCHING_INFORMATION: Match statement to paragraph letter. options are paragraph letters ["A", "B", "C"...] in questionGroup.
            5. MATCHING_HEADINGS: Match paragraph to list of headings. headings list as options ["i. Heading 1", "ii. Heading 2"...] in questionGroup.
            6. MATCHING_FEATURES: Match statements to a list of names/entities. options ["A. entity 1", "B. entity 2"...] in questionGroup.
            7. MATCHING_SENTENCE_ENDINGS: Match sentence beginning to ending. options ["A. ending 1", "B. ending 2"...] in questionGroup.
            8. SENTENCE_COMPLETION: Student fills the blank. correctAnswer is the word(s) from text.
            9. SUMMARY_COMPLETION: Summary paragraph with blanks like ___X___. groupContext contains the summary text, and questions have Blank X.
            10. DIAGRAM_LABEL_COMPLETION: Label a diagram/flowchart. correctAnswer is word(s) from text.
            11. SHORT_ANSWER: Short answer questions, correctAnswer from text.

            EVIDENCE MARKER INSTRUCTION:
            In each passage's `passageText` string, you MUST wrap the exact phrase or sentence that provides the evidence for each question in `[ANS_X]...[/ANS_X]` tags, where X is the sequential question number (from 1 to 40).
            For example:
            - Passage 1 text must contain `[ANS_1]...[/ANS_1]` through `[ANS_13]...[/ANS_13]`.
            - Passage 2 text must contain `[ANS_14]...[/ANS_14]` through `[ANS_26]...[/ANS_26]`.
            - Passage 3 text must contain `[ANS_27]...[/ANS_27]` through `[ANS_40]...[/ANS_40]`.

            OUTPUT FORMAT (Strict JSON):
            {
              "passages": [
                {
                  "passageIndex": 1,
                  "difficulty": "PASSAGE_1",
                  "title": "Title of Passage 1",
                  "passageText": "A. Paragraph 1 text with [ANS_1]evidence[/ANS_1]...\\n\\nB. Paragraph 2 text...",
                  "questionGroups": [
                    {
                      "groupLabel": "Questions 1-5: Do the following statements agree with the information given in the passage?",
                      "groupType": "TFNG",
                      "groupContext": null,
                      "wordLimit": null,
                      "options": null,
                      "questions": [
                        {
                          "orderIndex": 1,
                          "questionText": "Statement 1",
                          "correctAnswer": "TRUE",
                          "explanation": "Explanation for Q1."
                        }
                      ]
                    }
                  ]
                },
                {
                  "passageIndex": 2,
                  "difficulty": "PASSAGE_2",
                  "title": "Title of Passage 2",
                  "passageText": "A. Paragraph 1... with [ANS_14]evidence[/ANS_14]...",
                  "questionGroups": [ ... ]
                },
                {
                  "passageIndex": 3,
                  "difficulty": "PASSAGE_3",
                  "title": "Title of Passage 3",
                  "passageText": "A. Paragraph 1... with [ANS_27]evidence[/ANS_27]...",
                  "questionGroups": [ ... ]
                }
              ]
            }
            """;

    private static final String SYSTEM_PROMPT_FULL_TEST_GT = """
            You are an IELTS General Training Reading test generator. Generate a full General Training Reading mock test consisting of exactly 3 sections and exactly 40 questions total.

            STRUCTURE & DIFFICULTY:
            - Section 1: Social Survival. Contains 2 short factual texts (e.g. notices, advertisements, leaflets). Vocab is simple and practical. Exactly 13 questions (Q1 to Q13).
            - Section 2: Workplace Survival. Contains 2 texts related to professional settings (e.g., contracts, training, office policies). Vocab is practical but more professional. Exactly 13 questions (Q14 to Q26).
            - Section 3: General Reading. Contains 1 longer, more complex and descriptive text of general interest (magazine, book excerpt). Vocab is advanced but not highly technical. Exactly 14 questions (Q27 to Q40).
            - Total questions: Exactly 40. No more, no less.
            - Must label paragraphs in each text: A, B, C, D... (each paragraph starts with its letter label followed by a period and a space, e.g., "A. Paragraph text...").

            QUESTION TYPES (Choose a mix of 4-5 different types across the test from these 11 official types):
            1. MCQ (Multiple choice): Options label A, B, C, D in questions.
            2. TFNG (True/False/Not Given): correctAnswer is "TRUE", "FALSE", or "NOT GIVEN".
            3. YNNG (Yes/No/Not Given): correctAnswer is "YES", "NO", or "NOT GIVEN".
            4. MATCHING_INFORMATION: Match statement to paragraph letter. options are paragraph letters ["A", "B", "C"...] in questionGroup.
            5. MATCHING_HEADINGS: Match paragraph to list of headings. headings list as options ["i. Heading 1", "ii. Heading 2"...] in questionGroup.
            6. MATCHING_FEATURES: Match statements to a list of names/entities. options ["A. entity 1", "B. entity 2"...] in questionGroup.
            7. MATCHING_SENTENCE_ENDINGS: Match sentence beginning to ending. options ["A. ending 1", "B. ending 2"...] in questionGroup.
            8. SENTENCE_COMPLETION: Student fills the blank. correctAnswer is the word(s) from text.
            9. SUMMARY_COMPLETION: Summary paragraph with blanks like ___X___. groupContext contains the summary text, and questions have Blank X.
            10. DIAGRAM_LABEL_COMPLETION: Label a diagram/flowchart. correctAnswer is word(s) from text.
            11. SHORT_ANSWER: Short answer questions, correctAnswer from text.

            EVIDENCE MARKER INSTRUCTION:
            In each section's `passageText` string, you MUST wrap the exact phrase or sentence that provides the evidence for each question in `[ANS_X]...[/ANS_X]` tags, where X is the sequential question number (from 1 to 40).
            For example:
            - Section 1 text must contain `[ANS_1]...[/ANS_1]` through `[ANS_13]...[/ANS_13]`.
            - Section 2 text must contain `[ANS_14]...[/ANS_14]` through `[ANS_26]...[/ANS_26]`.
            - Section 3 text must contain `[ANS_27]...[/ANS_27]` through `[ANS_40]...[/ANS_40]`.

            OUTPUT FORMAT (Strict JSON):
            {
              "passages": [
                {
                  "passageIndex": 1,
                  "difficulty": "PASSAGE_1",
                  "title": "Title of Section 1 Texts",
                  "passageText": "A. Text 1 starts... with [ANS_1]evidence[/ANS_1]...\\n\\nB. Text 2 starts...",
                  "questionGroups": [
                    {
                      "groupLabel": "Questions 1-5: Do the following statements agree with the information given in the passage?",
                      "groupType": "TFNG",
                      "groupContext": null,
                      "wordLimit": null,
                      "options": null,
                      "questions": [
                        {
                          "orderIndex": 1,
                          "questionText": "Statement 1",
                          "correctAnswer": "TRUE",
                          "explanation": "Explanation for Q1."
                        }
                      ]
                    }
                  ]
                },
                {
                  "passageIndex": 2,
                  "difficulty": "PASSAGE_2",
                  "title": "Title of Section 2 Texts",
                  "passageText": "A. Workplace text... with [ANS_14]evidence[/ANS_14]...",
                  "questionGroups": [ ... ]
                },
                {
                  "passageIndex": 3,
                  "difficulty": "PASSAGE_3",
                  "title": "Title of Section 3 Text",
                  "passageText": "A. General reading text... with [ANS_27]evidence[/ANS_27]...",
                  "questionGroups": [ ... ]
                }
              ]
            }
            """;
}

