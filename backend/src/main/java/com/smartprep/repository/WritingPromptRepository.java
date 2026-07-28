package com.smartprep.repository;

import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.ContentStatus;
import com.smartprep.model.enums.EssayType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WritingPromptRepository extends JpaRepository<WritingPrompt, Long> {

    @Override
    @Query("SELECT p FROM WritingPrompt p WHERE p.promptId = :id AND p.deletedAt IS NULL")
    Optional<WritingPrompt> findById(@Param("id") Long id);

    @Query("SELECT p FROM WritingPrompt p WHERE p.promptId = :id")
    Optional<WritingPrompt> findIncludingDeletedById(@Param("id") Long id);

    @Override
    @Query("SELECT p FROM WritingPrompt p WHERE p.deletedAt IS NULL")
    Page<WritingPrompt> findAll(Pageable pageable);

    @Override
    @Query("SELECT p FROM WritingPrompt p WHERE p.deletedAt IS NULL")
    List<WritingPrompt> findAll();

    @Query("SELECT p FROM WritingPrompt p WHERE p.essayType = :essayType " +
           "AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<WritingPrompt> findByEssayTypeOrderByCreatedAtDesc(
            @Param("essayType") EssayType essayType);

    @Query("SELECT p FROM WritingPrompt p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<WritingPrompt> findAllByOrderByCreatedAtDesc();

    // Admin: paginated
    @Query("SELECT p FROM WritingPrompt p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Page<WritingPrompt> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT p FROM WritingPrompt p WHERE p.essayType = :essayType " +
           "AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    Page<WritingPrompt> findByEssayTypeOrderByCreatedAtDesc(
            @Param("essayType") EssayType essayType,
            Pageable pageable);

    @Query("SELECT p FROM WritingPrompt p WHERE p.contentStatus = :contentStatus " +
           "AND p.deletedAt IS NULL")
    Page<WritingPrompt> findByContentStatus(
            @Param("contentStatus") ContentStatus contentStatus,
            Pageable pageable);
}

