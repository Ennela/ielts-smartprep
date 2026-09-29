package com.smartprep.service.vocab;

import com.smartprep.repository.VocabularyRepository;
import com.smartprep.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VocabReminderService}.
 *
 * <p>Almost all of these are about not sending mail: the job's failure mode is not "the
 * email looked wrong", it is "a learner was emailed twice, or emailed at all when nobody
 * asked for it".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocabReminderServiceTest {

    @Mock
    private VocabularyRepository vocabularyRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private VocabReminderService service;

    /** A row as the grouping query returns it. */
    private static VocabularyRepository.DueReminderTarget target(
            Long userId, String email, String displayName, long dueCount) {
        return new VocabularyRepository.DueReminderTarget() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public String getDisplayName() {
                return displayName;
            }

            @Override
            public long getDueCount() {
                return dueCount;
            }
        };
    }

    @BeforeEach
    void setUp() {
        service = new VocabReminderService(vocabularyRepository, emailService, redisTemplate);
        ReflectionTestUtils.setField(service, "enabled", true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // By default the claim succeeds, i.e. nobody has been reminded yet today.
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Nested
    @DisplayName("when the feature is off")
    class Disabled {

        @Test
        @DisplayName("should not even look for recipients")
        void doesNothing() {
            ReflectionTestUtils.setField(service, "enabled", false);

            service.sendDueReminders();

            verifyNoInteractions(vocabularyRepository, emailService);
        }
    }

    @Nested
    @DisplayName("when it runs")
    class Runs {

        @Test
        @DisplayName("should send one email per learner with the number of words due")
        void sendsOnePerLearner() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(
                            target(2L, "noah@example.com", "Noah", 12),
                            target(3L, "mai@example.com", "Mai", 1)));

            service.sendDueReminders();

            verify(emailService).sendVocabReviewReminder("noah@example.com", "Noah", 12);
            verify(emailService).sendVocabReviewReminder("mai@example.com", "Mai", 1);
        }

        @Test
        @DisplayName("should send nothing when nobody has words due")
        void nobodyDue() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of());

            service.sendDueReminders();

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("should skip a learner already reminded today")
        void claimsOncePerDay() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(target(2L, "noah@example.com", "Noah", 5)));
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

            service.sendDueReminders();

            verify(emailService, never()).sendVocabReviewReminder(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should skip rather than risk a duplicate when Redis is unavailable")
        void failsClosed() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(target(2L, "noah@example.com", "Noah", 5)));
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenThrow(new IllegalStateException("redis down"));

            service.sendDueReminders();

            verify(emailService, never()).sendVocabReviewReminder(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("should keep going when one address fails")
        void oneFailureDoesNotStopTheRun() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(
                            target(2L, "broken@example.com", "Noah", 3),
                            target(3L, "mai@example.com", "Mai", 4)));
            org.mockito.Mockito.doThrow(new RuntimeException("smtp refused"))
                    .when(emailService).sendVocabReviewReminder(eq("broken@example.com"), anyString(), anyLong());

            service.sendDueReminders();

            verify(emailService).sendVocabReviewReminder("mai@example.com", "Mai", 4);
        }

        @Test
        @DisplayName("should not try to mail a row with no address")
        void skipsMissingAddress() {
            when(vocabularyRepository.findDueReminderTargets(any(LocalDateTime.class), any(Pageable.class)))
                    .thenReturn(List.of(target(2L, "  ", "Noah", 3)));

            service.sendDueReminders();

            verifyNoInteractions(emailService);
        }
    }
}
