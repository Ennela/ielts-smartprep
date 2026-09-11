package com.smartprep.repository;

import com.smartprep.model.entity.MockTestSubmission;
import com.smartprep.model.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MockTestSubmissionRepository extends JpaRepository<MockTestSubmission, Long> {
    List<MockTestSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);
    Optional<MockTestSubmission> findBySubmissionIdAndUserUserId(Long submissionId, Long userId);

    /**
     * Take ownership of a submission that needs grading again, atomically.
     *
     * <p>Returns the number of rows changed: 1 if this caller claimed it, 0 if someone else
     * already did or it was never eligible. That distinction is the whole point. Reading the
     * status and then writing it in two steps lets two concurrent retries both see a
     * retryable submission and both queue a grading run, which produces two sets of essay
     * rows for one sitting and whichever finishes last silently overwrites the other. A
     * single conditional UPDATE makes the claim the same operation as the check.
     *
     * <p>Eligible means FAILED, or stuck in GRADING since before {@code staleBefore} -- a run
     * whose process died mid-grading leaves the row in GRADING with nothing left to finish it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MockTestSubmission s
               SET s.status = com.smartprep.model.enums.SubmissionStatus.GRADING
             WHERE s.submissionId = :submissionId
               AND (s.status = :failed
                    OR (s.status = :grading AND s.submittedAt < :staleBefore))
            """)
    int claimForRegrade(@Param("submissionId") Long submissionId,
                        @Param("failed") SubmissionStatus failed,
                        @Param("grading") SubmissionStatus grading,
                        @Param("staleBefore") LocalDateTime staleBefore);
}
