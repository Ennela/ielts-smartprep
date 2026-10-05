package com.smartprep.repository;

import com.smartprep.model.entity.SpeakingAnswer;
import com.smartprep.model.entity.SpeakingPrompt;
import com.smartprep.model.entity.SpeakingSubmission;
import com.smartprep.model.entity.User;
import com.smartprep.model.enums.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Part 1/3 submission with one answer per question (V59), against MySQL: the answers are
 * saved with the submission, and the owner lookup returns them in question order.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SpeakingSubmissionRepositoryTest extends AbstractMySQLContainerTest {

    @Autowired private SpeakingSubmissionRepository submissionRepository;
    @Autowired private SpeakingPromptRepository promptRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("saves the answers with the submission and loads them in question order for the owner")
    void answersInOrder() {
        User user = userRepository.save(User.builder().username("speaking_answers_user").passwordHash("x")
                .email("speaking-answers@example.com").role(Role.STUDENT).build());
        SpeakingPrompt prompt = promptRepository.findByPartOrderByPromptIdAsc(1).get(0);
        SpeakingSubmission submission = SpeakingSubmission.builder()
                .user(user).prompt(prompt).durationSeconds(30)
                .overallBand(new BigDecimal("6.0")).fluencyBand(new BigDecimal("6.0"))
                .lexicalBand(new BigDecimal("6.0")).grammarBand(new BigDecimal("6.0"))
                .pronunciationBand(new BigDecimal("6.0")).feedbackJson("{}").build();
        // Added out of order on purpose: the order comes from question_index, not insertion.
        for (int i : List.of(1, 0)) {
            submission.getAnswers().add(SpeakingAnswer.builder().submission(submission).questionIndex(i)
                    .audioKey("k_q" + (i + 1) + ".webm").audioMimeType("audio/webm").durationSeconds(15)
                    .transcript("answer " + i).comment("comment " + i).build());
        }
        Long id = submissionRepository.save(submission).getSubmissionId();
        entityManager.flush();
        entityManager.clear();

        SpeakingSubmission found = submissionRepository.findBySubmissionIdAndUserUserId(id, user.getUserId()).orElseThrow();

        assertThat(found.getAudioKey()).isNull();
        assertThat(found.getAnswers()).extracting(SpeakingAnswer::getQuestionIndex).containsExactly(0, 1);
        assertThat(found.getAnswers().get(1).getTranscript()).isEqualTo("answer 1");
        assertThat(found.getAnswers().get(0).getAudioKey()).isEqualTo("k_q1.webm");
        assertThat(submissionRepository.findBySubmissionIdAndUserUserId(id, user.getUserId() + 1000)).isEmpty();
    }
}
