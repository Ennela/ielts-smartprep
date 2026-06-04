package com.smartprep.service.ai;

import org.springframework.stereotype.Component;

@Component
public class ListeningPromptBuilder {

    /**
     * Builds the generate prompt for IELTS Listening based on part number and optional topic.
     *
     * @param partNumber the part number (1-4)
     * @param topic      the topic (optional, if null/blank, AI will choose)
     * @return the generated prompt string
     */
    public String buildGeneratePrompt(int partNumber, String topic) {
        return buildGeneratePrompt(partNumber, topic, null);
    }

    public String buildGeneratePrompt(int partNumber, String topic, String focusQuestionType) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                You are a professional IELTS Listening test designer with 15 years of experience at Cambridge Assessment English. Create a complete, exam-quality IELTS Listening Part %d test that is indistinguishable from authentic Cambridge IELTS material.
                """, partNumber));
        if (focusQuestionType != null && !focusQuestionType.isBlank()) {
            sb.append(String.format("\n── Focus question type: %s (ensure one of the sections/question groups is designed to test this skill specifically) ──\n", focusQuestionType));
        }
        sb.append(String.format("""

                ═══════════════════════════════════════════
                PART %d — SPECIFICATIONS
                ═══════════════════════════════════════════
                """, partNumber));

        if (partNumber == 1) {
            sb.append("""
                    Script type  : DIALOGUE between exactly 2 named people
                    Context      : Everyday social transaction — choose one: campsite/hotel booking,
                                   gym membership enquiry, course registration, lost property report,
                                   survey interview, event ticket booking, or housing enquiry
                    Question Nos : Q1–Q10
                    Question mix : 7 FORM_COMPLETION + 3 MULTIPLE_CHOICE
                    Difficulty   : Band 4.5–6.0 (clear speech, moderate pace, common vocabulary)
                    Form title   : Create a realistic form title (e.g., "Green Valley Campsite — Booking Form")
                    Instruction  : "Complete the form below. Write NO MORE THAN THREE WORDS AND/OR A NUMBER for each answer."
                    """);
        } else if (partNumber == 2) {
            sb.append("""
                    Script type  : MONOLOGUE by a single speaker (tour guide, radio presenter, community officer)
                    Context      : Social/community setting — choose one: guided tour of a facility,
                                   radio programme about local services, talk about a community project,
                                   orientation talk for new members, or announcement about an event
                    Question Nos : Q11–Q20
                    Question mix : Q11–Q15 = 5 MULTIPLE_CHOICE  |  Q16–Q20 = 5 MATCHING
                    Difficulty   : Band 5.5–7.0 (natural pace, some less common vocabulary)
                    Matching set : Generate 8 options (A–H), only 5 are used as correct answers
                    Instruction  : Section A: "Choose the correct letter, A, B or C."
                                   Section B: "What does the speaker say about each of the following? Choose FIVE answers from the box."
                    """);
        } else if (partNumber == 3) {
            sb.append("""
                    Script type  : DIALOGUE between 2–3 named people (mix of male and female voices)
                    Context      : Academic/educational context — choose one: two students discussing
                                   a research assignment, student and tutor reviewing a project,
                                   group seminar preparation, or thesis/dissertation consultation
                    Question Nos : Q21–Q30
                    Question mix : Q21–Q25 = 5 MULTIPLE_CHOICE  |  Q26–Q30 = 5 SENTENCE_COMPLETION
                    Difficulty   : Band 6.0–7.5 (academic register, moderate-fast pace, opinions and reasoning)
                    Instruction  : Section A: "Choose the correct letter, A, B or C."
                                   Section B: "Complete the sentences below. Write NO MORE THAN TWO WORDS for each answer."
                    """);
        } else if (partNumber == 4) {
            sb.append("""
                    Script type  : MONOLOGUE (academic lecture or conference presentation)
                    Context      : University lecture or formal academic talk on ONE of:
                                   environmental science, urban planning, psychology, history of technology,
                                   linguistics, architecture, marine biology, or economics
                    Question Nos : Q31–Q40
                    Question mix : Q31–Q38 = 8 NOTE_COMPLETION  |  Q39–Q40 = 2 SENTENCE_COMPLETION
                    Difficulty   : Band 7.0–9.0 (academic vocabulary, complex reasoning, fast pace)
                    Notes title  : Lecture notes with a clear section structure matching the talk's flow
                    Instruction  : "Complete the notes below. Write NO MORE THAN TWO WORDS AND/OR A NUMBER for each answer."
                    """);
        }

        sb.append("\n── Optional topic (use if provided, otherwise choose a topic appropriate for the part) ──\n");
        if (topic != null && !topic.isBlank()) {
            sb.append(String.format("Topic (if specified): %s\n", topic));
        } else {
            sb.append("Topic (if specified): Choose a topic appropriate for the part\n");
        }

        sb.append("""

                ═══════════════════════════════════════════
                SCRIPT REQUIREMENTS
                ═══════════════════════════════════════════

                LENGTH:
                """);

        if (partNumber == 1) {
            sb.append("  Part 1 → 380–440 words\n");
        } else if (partNumber == 2) {
            sb.append("  Part 2 → 380–450 words\n");
        } else if (partNumber == 3) {
            sb.append("  Part 3 → 430–500 words\n");
        } else if (partNumber == 4) {
            sb.append("  Part 4 → 480–560 words\n");
        }

        sb.append("""

                NATURAL SPEECH MARKERS (mandatory):
                  Dialogue : "um", "actually", "let me check", "oh, wait", "I mean", "sorry, I said"
                  Monologue: "moving on to", "as I mentioned", "in other words", "this brings us to", "notably"

                ANSWER MARKERS (mandatory — embed in script):
                  Wrap the exact answer in the script with [ANS_X] tags where X = question number.
                  Example: "Our opening hours are from [ANS_1]nine in the morning[/ANS_1] until six."
                  This MUST be done for every question so the system can highlight answers in the transcript.

                SEQUENTIAL ORDER (mandatory):
                  [ANS_1] must appear before [ANS_2], which must appear before [ANS_3], and so on.
                  Never reverse the order of answers.

                ═══════════════════════════════════════════
                DISTRACTOR REQUIREMENTS (mandatory — min. 3)
                ═══════════════════════════════════════════

                You MUST include at least 3 of the following distractor techniques:

                  TYPE A — Changed mind / Self-correction:
                    Speaker first says X, then corrects to Y. Answer is Y; X is the trap.
                    Example: "It opens at nine — oh, actually, I meant ten on Mondays."
                    → Answer: ten / Trap: nine

                  TYPE B — Mentioned but eliminated:
                    A wrong option is explicitly raised and rejected during the conversation.
                    Example: "We could go by train — but actually the bus would be quicker."
                    → Answer: bus / Trap: train

                  TYPE C — Distractor before answer:
                    A plausible wrong answer is mentioned immediately before the correct answer.
                    Example: "It's not in the north wing — you'll find it in the east wing."
                    → Answer: east wing / Trap: north wing

                  TYPE D — Number/date confusion:
                    Two similar numbers or dates are mentioned; only one is the answer.
                    Example: "The price was £45, but with the discount it's now £35."
                    → Answer: 35 / Trap: 45

                  TYPE E — Synonym trap (MATCHING / MULTIPLE CHOICE):
                    The question uses a paraphrase; the wrong option uses the exact word from the script.
                    The correct answer requires understanding, not just keyword matching.

                For EACH distractor used: record the distractorType and provide a distractorNote explaining the trap in Vietnamese.

                ═══════════════════════════════════════════
                QUESTION WRITING RULES
                ═══════════════════════════════════════════

                1. PARAPHRASE (mandatory): Questions must NOT copy exact phrases from the script.
                   ✗ Bad : Script says "the cheapest option" → Question asks "the cheapest option"
                   ✓ Good: Script says "the cheapest option" → Question asks "the most affordable choice"

                2. COMPLETION ANSWERS: Must be extractable as 1–3 exact consecutive words from the script.
                   The answer in the JSON must EXACTLY match what appears between the [ANS_X] tags.

                3. MULTIPLE CHOICE: All 3 options (A/B/C) must appear plausible.
                   Wrong options should be mentioned somewhere in the script (as distractors).

                4. MATCHING: Generate 8 options (A–H) with descriptive labels.
                   Use only 5 as correct answers. The other 3 are extra options (plausible distractors).
                   Each matchingItem is a separate question (one letter per item).

                5. SENTENCE/NOTE COMPLETION: The sentence must be grammatically complete when the answer is inserted.

                ═══════════════════════════════════════════
                OUTPUT FORMAT — CRITICAL
                ═══════════════════════════════════════════

                Return ONLY a raw JSON object.
                No markdown code fences (no ```), no explanations, no preamble.
                The response MUST start with { and end with }.

                MATCHING question structure (each item = separate question object):
                  Each question has the SAME options array (A–H) but different questionText and correctAnswer.
                """);

        sb.append(String.format("""

                JSON structure:
                {
                  "partNumber": %d,
                  "topic": "The specific topic chosen",
                  "title": "Short descriptive title (e.g., 'Holiday Cottage Booking')",
                  "context": "One sentence describing the situation (used as subheading in UI)",
                  "scriptType": "DIALOGUE" | "MONOLOGUE",
                  "difficulty": "EASY" | "MEDIUM" | "HARD" | "VERY_HARD",
                  "speakers": [
                    { "name": "Full name and role (e.g., 'Sarah — Receptionist')", "label": "Sarah" },
                    { "name": "Second speaker", "label": "Mr. Davies" }
                  ],
                  "script": "Full transcript.\\n\\nFor DIALOGUE use:\\nSarah: sentence here\\nMr. Davies: reply here\\n\\nFor MONOLOGUE use continuous paragraphs.\\n\\nEmbed answer markers: [ANS_1]answer text here[/ANS_1]\\nALL 10 answer markers MUST be present.",
                  "sectionAInstruction": "Instruction for question group A (e.g., 'Complete the form below...')",
                  "sectionATitle": "Form/notes title or null",
                  "sectionBInstruction": "Instruction for question group B, or null if only 1 group",
                  "sectionBTitle": "Second section title or null",

                  // MATCHING ONLY — shared options for all matching questions in this part
                  "matchingOptions": null // or array for Part 2/3 with MATCHING questions,

                  "questions": [
                    {
                      "questionNumber": 1,
                      "questionType": "FORM_COMPLETION",
                      "questionText": "Customer's surname: _____",
                      "noteContext": null,      // Part 4 only: heading this note falls under
                      "options": null,           // null for completion types
                      "correctAnswer": "Davies", // EXACT text from between [ANS_1] tags
                      "answerEvidence": "The exact sentence in the script containing [ANS_1]Davies[/ANS_1]",
                      "distractorType": null,    // "TYPE_A" | "TYPE_B" | "TYPE_C" | "TYPE_D" | "TYPE_E" | null
                      "distractorNote": null,    // [VIETNAMESE] nếu có bẫy, mô tả cụ thể bẫy là gì
                      "explanation": "[VIETNAMESE] 2–3 câu: đáp án nằm ở đâu trong script, và nếu có bẫy thì giải thích học viên cần chú ý điều gì."
                    },
                    {
                      "questionNumber": 8,
                      "questionType": "MULTIPLE_CHOICE",
                      "questionText": "Why does the customer choose the standard room?",
                      "noteContext": null,
                      "options": [
                        { "label": "A", "content": "It has a better view" },
                        { "label": "B", "content": "It costs less than expected" },
                        { "label": "C", "content": "It is the only one available" }
                      ],
                      "correctAnswer": "B",
                      "answerEvidence": "exact script sentence proving B",
                      "distractorType": "TYPE_B",
                      "distractorNote": "[VIETNAMESE] Phương án A được đề cập trong hội thoại (speaker nhắc đến view) nhưng khách không quan tâm. C cũng bị loại bỏ rõ ràng khi nhân viên nói còn nhiều phòng.",
                      "explanation": "[VIETNAMESE] Đáp án B vì khách nói giá rẻ hơn dự kiến. Lưu ý: A và C đều được nhắc đến nhưng là bẫy điển hình của IELTS."
                    },
                    {
                      "questionNumber": 16,
                      "questionType": "MATCHING",
                      "questionText": "The Science Museum",   // item to match
                      "noteContext": null,
                      "options": null,   // null — dùng chung matchingOptions từ root level
                      "correctAnswer": "D",
                      "answerEvidence": "script sentence",
                      "distractorType": null,
                      "distractorNote": null,
                      "explanation": "[VIETNAMESE] The Science Museum — D vì speaker nói '...you'll need to book in advance...'"
                    },
                    {
                      "questionNumber": 35,
                      "questionType": "NOTE_COMPLETION",
                      "questionText": "First observed: in _____ communities",
                      "noteContext": "Background and Origins",   // heading in lecture notes
                      "options": null,
                      "correctAnswer": "coastal fishing",
                      "answerEvidence": "...first observed in [ANS_35]coastal fishing[/ANS_35] communities along the northern shore...",
                      "distractorType": "TYPE_C",
                      "distractorNote": "[VIETNAMESE] Ngay trước đó giảng viên nhắc đến 'farming communities' khiến người nghe dễ điền nhầm.",
                      "explanation": "[VIETNAMESE] Đáp án là 'coastal fishing' — từ khoá quan trọng cần lắng nghe sau cụm 'first observed in'."
                    }
                  ]
                }

                FINAL VALIDATION — check before outputting:
                ✓ Script contains exactly 10 answer markers: [ANS_1] through [ANS_10] (or [ANS_11]–[ANS_20] for Part 2, etc.)
                ✓ correctAnswer for each question EXACTLY matches text between its [ANS_X][/ANS_X] tags
                ✓ Answers appear in sequential order in the script (ANS_1 before ANS_2, etc.)
                ✓ At least 3 distractors with distractorType and distractorNote filled in
                ✓ Questions are paraphrased — no exact phrase copying from script
                ✓ All explanation and distractorNote fields are written in Vietnamese
                ✓ matchingOptions array has exactly 8 items (A–H) if MATCHING questions are present
                ✓ MATCHING questions: options field is null (they reference shared matchingOptions)
                ✓ NOTE_COMPLETION questions: noteContext is filled with the section heading
                ✓ JSON is valid and complete
                """, partNumber));

        return sb.toString();
    }
}
