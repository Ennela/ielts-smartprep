package com.smartprep.repository;

import com.smartprep.model.entity.WritingFullSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface WritingFullSubmissionRepository extends JpaRepository<WritingFullSubmission, Long> {

    List<WritingFullSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    /** One page of full sittings with both essays and their prompts fetched in the same query. */
    @EntityGraph(attributePaths = {"task1Submission", "task1Submission.prompt", "task2Submission", "task2Submission.prompt"})
    Page<WritingFullSubmission> findByUserUserId(Long userId, Pageable pageable);

    Optional<WritingFullSubmission> findByIdAndUserUserId(Long id, Long userId);
}
