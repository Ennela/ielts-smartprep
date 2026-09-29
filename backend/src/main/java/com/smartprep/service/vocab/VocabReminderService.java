package com.smartprep.service.vocab;

import com.smartprep.repository.VocabularyRepository;
import com.smartprep.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The daily "your words are waiting" email.
 *
 * <p>Spaced repetition only works if the learner comes back on the day a word is due, and
 * remembering to come back is exactly the job the algorithm exists to take off them. Without
 * this the schedule in the vocabulary table is a private fact the server keeps to itself.
 *
 * <p>Three things keep it from becoming spam:
 * <ul>
 *   <li>It is off unless {@code app.vocab-reminder.enabled} is set. A server with no SMTP
 *       credentials, which is every development machine, sends nothing and logs nothing.</li>
 *   <li>The query only returns learners who switched the preference on <em>and</em> confirmed
 *       their address, so nobody is mailed who did not ask to be.</li>
 *   <li>A Redis key marks each learner as reminded for the day, so a restart at the wrong
 *       minute, or a second instance behind a load balancer, cannot send twice.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VocabReminderService {

    /**
     * Upper bound on one run.
     *
     * <p>Not a page size to iterate over: it is a deliberate ceiling. If this product ever
     * has more learners than this with words due on the same morning, sending the whole
     * backlog from one scheduled thread is the wrong design, and the log line below is how
     * that shows up rather than a mail server dropping the connection halfway through.
     */
    private static final int MAX_RECIPIENTS_PER_RUN = 500;

    /** Long enough to outlive the day the key names, short enough not to accumulate. */
    private static final Duration ALREADY_SENT_TTL = Duration.ofDays(2);

    private final VocabularyRepository vocabularyRepository;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.vocab-reminder.enabled:false}")
    private boolean enabled;

    /**
     * Send today's reminders.
     *
     * <p>Runs on the schedule in {@code app.vocab-reminder.cron}, in the zone the learners
     * live in rather than the server's, so "07:00" means their morning.
     */
    @Scheduled(cron = "${app.vocab-reminder.cron:0 0 7 * * *}",
               zone = "${app.vocab-reminder.zone:Asia/Ho_Chi_Minh}")
    public void sendDueReminders() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<VocabularyRepository.DueReminderTarget> targets =
                vocabularyRepository.findDueReminderTargets(
                        LocalDateTime.now(), PageRequest.of(0, MAX_RECIPIENTS_PER_RUN));

        if (targets.isEmpty()) {
            log.info("Vocabulary reminders: nobody has words due");
            return;
        }
        if (targets.size() == MAX_RECIPIENTS_PER_RUN) {
            log.warn("Vocabulary reminders hit the per-run ceiling of {}; some learners were "
                    + "not reminded today", MAX_RECIPIENTS_PER_RUN);
        }

        int sent = 0;
        int skipped = 0;
        for (VocabularyRepository.DueReminderTarget target : targets) {
            if (target.getEmail() == null || target.getEmail().isBlank()) {
                continue;
            }
            if (!claimForToday(target.getUserId(), today)) {
                skipped++;
                continue;
            }
            try {
                emailService.sendVocabReviewReminder(
                        target.getEmail(), target.getDisplayName(), target.getDueCount());
                sent++;
            } catch (Exception e) {
                // One bad address must not stop the rest of the run. EmailService already
                // swallows the usual MessagingException, so reaching here means something
                // more unusual, and it is still not worth abandoning everyone else for.
                log.warn("Vocabulary reminder failed for user {} ({})",
                        target.getUserId(), e.getClass().getSimpleName());
            }
        }

        log.info("Vocabulary reminders: {} sent, {} already reminded today", sent, skipped);
    }

    /**
     * Claim the right to remind this learner today, once.
     *
     * <p>Fails closed. If Redis cannot answer, the claim is refused and the learner simply
     * does not get today's reminder: a missed nudge costs them one day, whereas sending a
     * duplicate because the guard was unavailable costs their trust. The rate limiter in
     * this codebase makes the same trade for the same reason.
     */
    private boolean claimForToday(Long userId, LocalDate day) {
        String key = "vocab-reminder:" + userId + ":" + day;
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, "1", ALREADY_SENT_TTL));
        } catch (Exception e) {
            log.warn("Could not claim the vocabulary reminder for user {} ({}); skipping to "
                    + "avoid sending it twice", userId, e.getClass().getSimpleName());
            return false;
        }
    }
}
