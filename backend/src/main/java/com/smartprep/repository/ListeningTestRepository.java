package com.smartprep.repository;

import com.smartprep.model.entity.ListeningTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListeningTestRepository extends JpaRepository<ListeningTest, Long> {

    List<ListeningTest> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    @Query("SELECT tp.part.partId FROM ListeningTestPart tp " +
           "WHERE tp.test.user.userId = :userId AND tp.test.submittedAt > :since")
    List<Long> findRecentPartIds(@Param("userId") Long userId,
                                 @Param("since") LocalDateTime since);

    Optional<ListeningTest> findByTestIdAndUserUserId(Long testId, Long userId);

    /**
     * True once this user has submitted a test containing the part, which is what
     * gates the post-exam AI endpoints: both of them quote the transcript back.
     */
    @Query("SELECT CASE WHEN COUNT(tp) > 0 THEN true ELSE false END FROM ListeningTestPart tp " +
           "WHERE tp.test.user.userId = :userId AND tp.part.partId = :partId")
    boolean existsSubmittedPart(@Param("userId") Long userId,
                                @Param("partId") Long partId);
}
