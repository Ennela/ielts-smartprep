package com.smartprep.service.vocab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartprep.dto.response.VocabInsight;
import com.smartprep.exception.InvalidAiResponseException;
import com.smartprep.service.ai.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests of the explanation pipeline with the model stubbed out.
 *
 * <p>These cannot judge whether Gemini's linguistics are right — that needs a human reading
 * real output. What they do establish is that a well-formed answer for the three cases the
 * feature exists for survives parsing, validation and storage with its teaching intact, and
 * that a malformed one is rejected rather than shown.
 */
@ExtendWith(MockitoExtension.class)
class VocabAiInsightTest {

    @Mock
    private GeminiClient geminiClient;

    private VocabAiService vocabAiService;

    @BeforeEach
    void setUp() {
        vocabAiService = new VocabAiService(geminiClient, new ObjectMapper(), new VocabInsightValidator());
    }

    /** Stubs the client so the service's own parser runs against a canned model response. */
    @SuppressWarnings("unchecked")
    private void modelReturns(String json) {
        when(geminiClient.generateAndParse(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    GeminiClient.CheckedFunction<String, Object> parser = invocation.getArgument(2);
                    return parser.apply(json);
                });
    }

    @Test
    @DisplayName("Scenario A: a near-synonym distinction survives into the stored explanation")
    void nearSynonymDifferentiation() {
        modelReturns("""
                {
                  "word": "belief",
                  "partOfSpeech": "noun",
                  "coreMeaningVi": "Niem tin, dieu ma ai do cho la dung.",
                  "registers": ["neutral"],
                  "senses": [
                    {
                      "meaningVi": "Dieu ma mot nguoi chap nhan la dung.",
                      "contextVi": "Dung khi noi ve quan diem hoac dieu ai do cho la dung.",
                      "examples": [
                        { "english": "It is my belief that education changes lives.",
                          "vietnamese": "Toi tin rang giao duc thay doi cuoc song.",
                          "whyVi": "Belief nhan manh dieu nguoi noi cho la dung.",
                          "setting": "IELTS_WRITING" }
                      ]
                    }
                  ],
                  "synonymComparisons": [
                    {
                      "focus": "belief vs faith vs trust",
                      "words": [
                        { "word": "belief", "coreIdeaVi": "Chap nhan dieu gi do la dung.",
                          "typicalContextVi": "Quan diem, y kien.", "nuanceVi": "Tap trung vao noi dung." },
                        { "word": "faith", "coreIdeaVi": "Niem tin sau sac, khong can bang chung.",
                          "typicalContextVi": "Ton giao, hy vong.", "nuanceVi": "Mang mau sac cam xuc." },
                        { "word": "trust", "coreIdeaVi": "Tin vao su dang tin cay cua ai do.",
                          "typicalContextVi": "Quan he giua nguoi voi nguoi.", "nuanceVi": "Nhan manh su dua vao duoc." }
                      ],
                      "contrastExamples": [
                        { "english": "I trust him.", "vietnamese": "Toi tin anh ay.",
                          "whyVi": "Noi ve su dang tin cay." },
                        { "english": "I have faith in him.", "vietnamese": "Toi dat niem tin vao anh ay.",
                          "whyVi": "Manh hon, mang tinh ca nhan." }
                      ],
                      "interchangeabilityVi": "Khong phai luc nao cung thay the duoc cho nhau."
                    }
                  ]
                }
                """);

        VocabInsight insight = vocabAiService.generateInsight("belief", "noun", null);

        assertThat(insight.getSynonymComparisons()).hasSize(1);
        assertThat(insight.getSynonymComparisons().get(0).getWords())
                .extracting(VocabInsight.ComparedWord::getWord)
                .containsExactly("belief", "faith", "trust");
        assertThat(insight.getSynonymComparisons().get(0).getContrastExamples()).hasSize(2);
        assertThat(insight.getSynonymComparisons().get(0).getInterchangeabilityVi()).isNotBlank();
    }

    @Test
    @DisplayName("Scenario B: article usage is carried as a grammar pattern, not a separate entry")
    void contextualArticleUsage() {
        modelReturns("""
                {
                  "word": "chance",
                  "partOfSpeech": "noun",
                  "coreMeaningVi": "Co hoi, kha nang mot dieu gi do xay ra.",
                  "registers": ["neutral"],
                  "senses": [
                    { "meaningVi": "Co hoi de lam mot viec gi do.",
                      "contextVi": "Khi noi ve dip thuan loi.",
                      "examples": [ { "english": "I had a chance to study abroad.",
                                      "vietnamese": "Toi da co co hoi du hoc.",
                                      "whyVi": "A chance gioi thieu mot co hoi moi." } ] }
                  ],
                  "grammarPatterns": [
                    { "pattern": "a chance to do sth", "explanationVi": "Gioi thieu mot co hoi moi.",
                      "example": "She got a chance to speak." },
                    { "pattern": "the chance", "explanationVi": "Chi mot co hoi cu the ma ca hai ben deu biet.",
                      "example": "She took the chance we offered her." },
                    { "pattern": "a chance of doing sth", "explanationVi": "Noi ve xac suat, khong phai dip may.",
                      "example": "There is a chance of rain tonight." },
                    { "pattern": "take a chance", "explanationVi": "Chap nhan rui ro.",
                      "example": "He took a chance and applied." }
                  ]
                }
                """);

        VocabInsight insight = vocabAiService.generateInsight("chance", "noun", null);

        assertThat(insight.getGrammarPatterns())
                .extracting(VocabInsight.GrammarPattern::getPattern)
                .contains("a chance to do sth", "the chance", "a chance of doing sth");
    }

    @Test
    @DisplayName("Scenario C: an idiom keeps its register and its context")
    void idiomaticExpression() {
        modelReturns("""
                {
                  "word": "make a point",
                  "partOfSpeech": "phrase",
                  "coreMeaningVi": "Neu ra mot y kien dang duoc chu y.",
                  "registers": ["neutral", "formal"],
                  "registerNoteVi": "Trung tinh khi tranh luan hang ngay, trang trong hon trong van viet.",
                  "contextSummaryVi": "Nguoi noi muon nhan manh rang y vua neu la dang luu y.",
                  "senses": [
                    { "meaningVi": "Neu ra mot luan diem.",
                      "registers": ["neutral"],
                      "contextVi": "Trong thao luan hoac tranh luan.",
                      "examples": [ { "english": "You make a good point about cost.",
                                      "vietnamese": "Ban neu ra mot y rat dung ve chi phi.",
                                      "whyVi": "Cong nhan y kien cua nguoi khac.",
                                      "setting": "IELTS_SPEAKING" } ] },
                    { "meaningVi": "Co y lam dieu gi do mot cach co chu dich.",
                      "registers": ["formal"],
                      "contextVi": "Trong cau make a point of doing sth.",
                      "examples": [ { "english": "She makes a point of replying to every email.",
                                      "vietnamese": "Co ay luon co y tra loi moi email.",
                                      "whyVi": "Nghia khac han nghia neu luan diem." } ] }
                  ]
                }
                """);

        VocabInsight insight = vocabAiService.generateInsight("make a point", "phrase", null);

        assertThat(insight.getRegisters()).containsExactly("neutral", "formal");
        assertThat(insight.getRegisterNoteVi()).isNotBlank();
        // Two genuinely different meanings, kept apart rather than collapsed into one gloss.
        assertThat(insight.getSenses()).hasSize(2);
        assertThat(insight.getSenses().get(1).getMeaningVi()).contains("chu dich");
    }

    @Test
    @DisplayName("Scenario D: a malformed response is rejected rather than half-rendered")
    void malformedResponseIsRejected() {
        modelReturns("I am afraid I cannot help with that.");

        assertThatThrownBy(() -> vocabAiService.generateInsight("belief", "noun", null))
                .isInstanceOf(InvalidAiResponseException.class);
    }

    @Test
    @DisplayName("should send the saved context to the model so the right sense is explained first")
    void contextReachesThePrompt() {
        modelReturns("""
                { "word": "chance", "coreMeaningVi": "Co hoi.",
                  "senses": [ { "meaningVi": "Co hoi." } ] }
                """);

        vocabAiService.generateInsight("chance", "noun", "There is a chance of rain tonight.");

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateAndParse(anyString(), userPrompt.capture(), any());
        assertThat(userPrompt.getValue()).contains("There is a chance of rain tonight.");
        assertThat(userPrompt.getValue()).contains("Part of speech recorded by the learner: noun");
    }
}
