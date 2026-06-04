package com.smartprep.repository;

import com.smartprep.model.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {

    Optional<Vocabulary> findByUserUserIdAndWord(Long userId, String word);

    List<Vocabulary> findByUserUserIdAndDueDateBeforeOrderByDueDateAsc(Long userId, LocalDateTime now);

    List<Vocabulary> findByUserUserIdOrderByCreatedAtDesc(Long userId);
}
