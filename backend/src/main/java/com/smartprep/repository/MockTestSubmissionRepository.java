package com.smartprep.repository;

import com.smartprep.model.entity.MockTestSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockTestSubmissionRepository extends JpaRepository<MockTestSubmission, Long> {
    List<MockTestSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);
    Optional<MockTestSubmission> findBySubmissionIdAndUserUserId(Long submissionId, Long userId);
}
