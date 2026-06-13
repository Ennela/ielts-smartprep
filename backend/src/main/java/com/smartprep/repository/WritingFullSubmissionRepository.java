package com.smartprep.repository;

import com.smartprep.model.entity.WritingFullSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WritingFullSubmissionRepository extends JpaRepository<WritingFullSubmission, Long> {

    List<WritingFullSubmission> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    Optional<WritingFullSubmission> findByIdAndUserUserId(Long id, Long userId);
}
