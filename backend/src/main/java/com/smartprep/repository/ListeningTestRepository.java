package com.smartprep.repository;

import com.smartprep.model.entity.ListeningTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ListeningTestRepository extends JpaRepository<ListeningTest, Long> {

    List<ListeningTest> findByUserUserIdOrderBySubmittedAtDesc(Long userId);

    @Query("SELECT tp.part.partId FROM ListeningTestPart tp " +
           "WHERE tp.test.user.userId = :userId AND tp.test.submittedAt > :since")
    List<Long> findRecentPartIds(@Param("userId") Long userId,
                                 @Param("since") LocalDateTime since);
}
