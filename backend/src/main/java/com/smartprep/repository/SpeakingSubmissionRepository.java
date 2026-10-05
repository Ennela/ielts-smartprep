package com.smartprep.repository;

import com.smartprep.model.entity.SpeakingSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpeakingSubmissionRepository extends JpaRepository<SpeakingSubmission, Long> {

    @EntityGraph(attributePaths = "prompt")
    Page<SpeakingSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId, Pageable pageable);

    /** Scoped to the owner, so another user's id reads as "not found". Loads the answers too. */
    @EntityGraph(attributePaths = {"prompt", "answers"})
    Optional<SpeakingSubmission> findBySubmissionIdAndUserUserId(Long submissionId, Long userId);
}
