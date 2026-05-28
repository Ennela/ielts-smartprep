package com.smartprep.repository;

import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WritingPromptRepository extends JpaRepository<WritingPrompt, Long> {

    List<WritingPrompt> findByEssayTypeOrderByCreatedAtDesc(EssayType essayType);

    List<WritingPrompt> findAllByOrderByCreatedAtDesc();
}
