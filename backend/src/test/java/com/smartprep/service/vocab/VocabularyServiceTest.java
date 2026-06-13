package com.smartprep.service.vocab;

import com.smartprep.dto.request.VocabBulkSaveRequest;
import com.smartprep.dto.request.VocabCreateRequest;
import com.smartprep.dto.response.VocabResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.User;
import com.smartprep.model.entity.Vocabulary;
import com.smartprep.model.enums.SkillType;
import com.smartprep.repository.UserRepository;
import com.smartprep.repository.VocabularyRepository;
import com.smartprep.repository.MockTestSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VocabularyService}.
 * Repository and AI dependencies are fully mocked.
 */
@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MockTestSubmissionRepository mockTestSubmissionRepository;

    @Mock
    private Sm2Service sm2Service;

    @Mock
    private VocabAiService vocabAiService;

    @Mock
    private List<VocabSourceResolver> resolvers;

    @InjectMocks
    private VocabularyService vocabularyService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .userId(1L)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hash")
                .build();
    }

    // ===================================================================
    //  addVocabulary
    // ===================================================================

    @Nested
    @DisplayName("addVocabulary")
    class AddVocabulary {

        @Test
        @DisplayName("should save and return response for valid request")
        void success() {
            VocabCreateRequest request = new VocabCreateRequest();
            request.setWord("ubiquitous");
            request.setMeaningVi("phổ biến");
            request.setPartOfSpeech("adjective");
            request.setSourceSkill("READING");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(vocabularyRepository.findByUserUserIdAndWord(1L, "ubiquitous")).thenReturn(Optional.empty());
            when(vocabularyRepository.save(any(Vocabulary.class))).thenAnswer(invocation -> {
                Vocabulary v = invocation.getArgument(0);
                v.setVocabId(100L);
                return v;
            });

            VocabResponse response = vocabularyService.addVocabulary(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getWord()).isEqualTo("ubiquitous");
            assertThat(response.getMeaningVi()).isEqualTo("phổ biến");
            assertThat(response.getSourceSkill()).isEqualTo("READING");

            // Verify save was called
            ArgumentCaptor<Vocabulary> captor = ArgumentCaptor.forClass(Vocabulary.class);
            verify(vocabularyRepository).save(captor.capture());
            assertThat(captor.getValue().getSourceSkill()).isEqualTo(SkillType.READING);
        }

        @Test
        @DisplayName("should throw when user not found")
        void userNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vocabularyService.addVocabulary(99L, new VocabCreateRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("should throw when word already exists")
        void duplicateWord() {
            VocabCreateRequest request = new VocabCreateRequest();
            request.setWord("duplicate");
            request.setMeaningVi("trùng");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(vocabularyRepository.findByUserUserIdAndWord(1L, "duplicate"))
                    .thenReturn(Optional.of(Vocabulary.builder().build()));

            assertThatThrownBy(() -> vocabularyService.addVocabulary(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("đã tồn tại");
        }

        @Test
        @DisplayName("invalid sourceSkill is ignored gracefully")
        void invalidSourceSkill() {
            VocabCreateRequest request = new VocabCreateRequest();
            request.setWord("test");
            request.setMeaningVi("kiểm tra");
            request.setSourceSkill("INVALID_SKILL");

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(vocabularyRepository.findByUserUserIdAndWord(1L, "test")).thenReturn(Optional.empty());
            when(vocabularyRepository.save(any(Vocabulary.class))).thenAnswer(inv -> {
                Vocabulary v = inv.getArgument(0);
                v.setVocabId(101L);
                return v;
            });

            VocabResponse response = vocabularyService.addVocabulary(1L, request);

            assertThat(response.getSourceSkill()).isNull();
        }
    }

    // ===================================================================
    //  getDueVocabularies
    // ===================================================================

    @Nested
    @DisplayName("getDueVocabularies")
    class GetDueVocabularies {

        @Test
        @DisplayName("should return due vocabularies mapped to response DTOs")
        void returnsDueItems() {
            Vocabulary v1 = buildVocab(1L, "hello", "xin chào", 2.5, 1, 1);
            Vocabulary v2 = buildVocab(2L, "world", "thế giới", 2.3, 6, 2);

            when(vocabularyRepository.findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(List.of(v1, v2));

            List<VocabResponse> result = vocabularyService.getDueVocabularies(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getWord()).isEqualTo("hello");
            assertThat(result.get(1).getWord()).isEqualTo("world");
        }

        @Test
        @DisplayName("should return empty list when nothing is due")
        void returnsEmptyWhenNothingDue() {
            when(vocabularyRepository.findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            assertThat(vocabularyService.getDueVocabularies(1L)).isEmpty();
        }
    }

    // ===================================================================
    //  reviewVocabulary
    // ===================================================================

    @Nested
    @DisplayName("reviewVocabulary")
    class ReviewVocabulary {

        @Test
        @DisplayName("should call Sm2Service and save updated vocab")
        void success() {
            Vocabulary vocab = buildVocab(10L, "apple", "quả táo", 2.5, 1, 1);
            vocab.setUser(testUser);

            when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocab));
            when(vocabularyRepository.save(any(Vocabulary.class))).thenReturn(vocab);

            VocabResponse result = vocabularyService.reviewVocabulary(1L, 10L, "GOOD");

            verify(sm2Service).updateReview(vocab, "GOOD");
            verify(vocabularyRepository).save(vocab);
            assertThat(result.getWord()).isEqualTo("apple");
        }

        @Test
        @DisplayName("should throw when vocab not found")
        void vocabNotFound() {
            when(vocabularyRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vocabularyService.reviewVocabulary(1L, 99L, "GOOD"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when user does not own the vocab")
        void notAuthorized() {
            User otherUser = User.builder().userId(2L).build();
            Vocabulary vocab = buildVocab(10L, "cat", "con mèo", 2.5, 0, 0);
            vocab.setUser(otherUser);

            when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocab));

            assertThatThrownBy(() -> vocabularyService.reviewVocabulary(1L, 10L, "GOOD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not authorized");
        }
    }

    // ===================================================================
    //  deleteVocabulary
    // ===================================================================

    @Nested
    @DisplayName("deleteVocabulary")
    class DeleteVocabulary {

        @Test
        @DisplayName("should delete vocab owned by the user")
        void success() {
            Vocabulary vocab = buildVocab(10L, "dog", "con chó", 2.5, 0, 0);
            vocab.setUser(testUser);

            when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocab));

            vocabularyService.deleteVocabulary(1L, 10L);

            verify(vocabularyRepository).delete(vocab);
        }

        @Test
        @DisplayName("should throw when vocab not found")
        void notFound() {
            when(vocabularyRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> vocabularyService.deleteVocabulary(1L, 99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when user does not own the vocab")
        void notAuthorized() {
            User otherUser = User.builder().userId(2L).build();
            Vocabulary vocab = buildVocab(10L, "bird", "con chim", 2.5, 0, 0);
            vocab.setUser(otherUser);

            when(vocabularyRepository.findById(10L)).thenReturn(Optional.of(vocab));

            assertThatThrownBy(() -> vocabularyService.deleteVocabulary(1L, 10L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ===================================================================
    //  getAllVocabularies
    // ===================================================================

    @Test
    @DisplayName("getAllVocabularies returns all items for user")
    void getAllVocabularies() {
        when(vocabularyRepository.findByUserUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(
                        buildVocab(1L, "a", "a_vn", 2.5, 0, 0),
                        buildVocab(2L, "b", "b_vn", 2.5, 0, 0)
                ));

        List<VocabResponse> result = vocabularyService.getAllVocabularies(1L);
        assertThat(result).hasSize(2);
    }

    // ===================================================================
    //  suggestVocabulary
    // ===================================================================

    @Nested
    @DisplayName("suggestVocabulary")
    class SuggestVocabulary {

        @Test
        @DisplayName("should suggest and filter out existing words and AI duplicates")
        void suggestAndFilter() {
            Vocabulary existingVocab = Vocabulary.builder().word("ubiquitous").build();
            when(vocabularyRepository.findByUserUserIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(existingVocab));

            VocabSourceResolver mockResolver = mock(VocabSourceResolver.class);
            when(mockResolver.getSkillType()).thenReturn(SkillType.READING);
            when(mockResolver.resolveSourceText(100L)).thenReturn("Test passage text with some words");
            
            when(resolvers.stream()).thenAnswer(inv -> java.util.stream.Stream.of(mockResolver));

            VocabAiService.SuggestedVocab s1 = new VocabAiService.SuggestedVocab("ubiquitous", "/juːˈbɪkwɪtəs/", "adjective", "phổ biến", "Mobile phones are ubiquitous", "ubiquitous presence", "B2");
            VocabAiService.SuggestedVocab s2 = new VocabAiService.SuggestedVocab("pristine", "/ˈprɪstiːn/", "adjective", "nguyên sơ", "A pristine beach", "pristine environment", "C1");
            VocabAiService.SuggestedVocab s3 = new VocabAiService.SuggestedVocab("PRISTINE", "/ˈprɪstiːn/", "adjective", "nguyên sơ", "A pristine beach", "pristine environment", "C1");
            VocabAiService.SuggestedVocab s4 = new VocabAiService.SuggestedVocab("", "", "", "", "", "", "");

            when(vocabAiService.suggestVocabulary("Test passage text with some words")).thenReturn(List.of(s1, s2, s3, s4));

            List<VocabAiService.SuggestedVocab> result = vocabularyService.suggestVocabulary(1L, "READING", 100L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getWord()).isEqualTo("pristine");
        }
    }

    // ===================================================================
    //  bulkSaveVocabulary
    // ===================================================================

    @Nested
    @DisplayName("bulkSaveVocabulary")
    class BulkSaveVocabulary {

        @Test
        @DisplayName("should merge fields when word already exists")
        void mergeExistingWord() {
            VocabBulkSaveRequest request = new VocabBulkSaveRequest();
            VocabCreateRequest item = new VocabCreateRequest();
            item.setWord("ubiquitous");
            item.setMeaningVi("ở khắp nơi");
            item.setPhonetic("/juːˈbɪkwɪtəs/");
            item.setSourceSkill("READING");
            item.setSourceRef("Passage 1");
            request.setVocabularies(List.of(item));

            Vocabulary existingVocab = Vocabulary.builder()
                    .vocabId(500L)
                    .word("ubiquitous")
                    .meaningVi("")
                    .build();

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(vocabularyRepository.findByUserUserIdAndWord(1L, "ubiquitous"))
                    .thenReturn(Optional.of(existingVocab));

            int count = vocabularyService.bulkSaveVocabulary(1L, request);

            assertThat(count).isEqualTo(1);
            verify(vocabularyRepository).save(existingVocab);
            assertThat(existingVocab.getMeaningVi()).isEqualTo("ở khắp nơi");
            assertThat(existingVocab.getPhonetic()).isEqualTo("/juːˈbɪkwɪtəs/");
            assertThat(existingVocab.getSourceSkill()).isEqualTo(SkillType.READING);
            assertThat(existingVocab.getSourceRef()).isEqualTo("Passage 1");
        }

        @Test
        @DisplayName("should save new word if not exists")
        void saveNewWord() {
            VocabBulkSaveRequest request = new VocabBulkSaveRequest();
            VocabCreateRequest item = new VocabCreateRequest();
            item.setWord("novel");
            item.setMeaningVi("mới lạ");
            item.setPartOfSpeech("adjective");
            request.setVocabularies(List.of(item));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(vocabularyRepository.findByUserUserIdAndWord(1L, "novel")).thenReturn(Optional.empty());

            int count = vocabularyService.bulkSaveVocabulary(1L, request);

            assertThat(count).isEqualTo(1);
            verify(vocabularyRepository).save(any(Vocabulary.class));
        }

        @Test
        @DisplayName("should return 0 for empty list")
        void emptyRequest() {
            int count = vocabularyService.bulkSaveVocabulary(1L, new VocabBulkSaveRequest());
            assertThat(count).isZero();
        }
    }

    // ===================================================================
    //  Helpers
    // ===================================================================

    private Vocabulary buildVocab(Long id, String word, String meaningVi,
                                   double ef, int interval, int reps) {
        return Vocabulary.builder()
                .vocabId(id)
                .word(word)
                .meaningVi(meaningVi)
                .easeFactor(ef)
                .intervalDays(interval)
                .repetitions(reps)
                .dueDate(LocalDateTime.now())
                .build();
    }
}
