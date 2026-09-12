package com.smartprep.repository;

import com.smartprep.model.entity.WritingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, Long> {

    List<WritingSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    /** One page of the user's essays with their prompts fetched alongside, not one query each. */
    @EntityGraph(attributePaths = "prompt")
    Page<WritingSubmission> findByUserUserId(Long userId, Pageable pageable);

    Optional<WritingSubmission> findBySubmissionIdAndUserUserId(Long submissionId, Long userId);
}
