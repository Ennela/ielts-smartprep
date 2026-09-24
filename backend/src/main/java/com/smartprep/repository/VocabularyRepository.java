package com.smartprep.repository;

import com.smartprep.model.entity.Vocabulary;
import com.smartprep.model.enums.SkillType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * One page of a user's words, filtered the way the vocabulary page filters them.
     *
     * The page used to fetch every word and filter in the browser, so the cost grew
     * with the size of the user's own collection. Null filters match everything.
     */
    @Query("SELECT v FROM Vocabulary v WHERE v.user.userId = :userId "
            + "AND (:cefrLevel IS NULL OR v.cefrLevel = :cefrLevel) "
            + "AND (:sourceSkill IS NULL OR v.sourceSkill = :sourceSkill) "
            + "AND (:search IS NULL OR LOWER(v.word) LIKE :search "
            + "     OR LOWER(v.meaningVi) LIKE :search "
            + "     OR LOWER(v.partOfSpeech) LIKE :search) "
            + "ORDER BY v.createdAt DESC")
    Page<Vocabulary> findForUser(@Param("userId") Long userId,
                                 @Param("search") String search,
                                 @Param("cefrLevel") String cefrLevel,
                                 @Param("sourceSkill") SkillType sourceSkill,
                                 Pageable pageable);

    long countByUserUserIdAndRepetitionsGreaterThanEqual(Long userId, int repetitions);

    long countByUserUserIdAndRepetitionsLessThan(Long userId, int repetitions);

    long countByUserUserIdAndDueDateBefore(Long userId, LocalDateTime now);
}
