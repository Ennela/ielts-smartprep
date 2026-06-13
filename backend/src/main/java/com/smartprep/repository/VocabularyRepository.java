package com.smartprep.repository;

import com.smartprep.model.entity.Vocabulary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    Optional<Vocabulary> findByUserUserIdAndWord(Long userId, String word);

    List<Vocabulary> findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(Long userId, LocalDateTime now);

    Page<Vocabulary> findByUserUserIdAndDueDateBefore(Long userId, LocalDateTime now, Pageable pageable);

    List<Vocabulary> findByUserUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserUserIdAndRepetitionsGreaterThanEqual(Long userId, int repetitions);

    long countByUserUserIdAndRepetitionsLessThan(Long userId, int repetitions);

    long countByUserUserIdAndDueDateBefore(Long userId, LocalDateTime now);
}
