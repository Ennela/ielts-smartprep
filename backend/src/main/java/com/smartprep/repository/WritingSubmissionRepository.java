package com.smartprep.repository;

import com.smartprep.model.entity.WritingSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WritingSubmissionRepository extends JpaRepository<WritingSubmission, Long> {

    List<WritingSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    Optional<WritingSubmission> findBySubmissionIdAndUserUserId(Long submissionId, Long userId);
}
