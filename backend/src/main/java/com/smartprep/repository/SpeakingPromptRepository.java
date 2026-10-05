package com.smartprep.repository;

import com.smartprep.model.entity.SpeakingPrompt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpeakingPromptRepository extends JpaRepository<SpeakingPrompt, Long> {
    List<SpeakingPrompt> findByPartOrderByPromptIdAsc(Integer part);

    List<SpeakingPrompt> findAllByOrderByPartAscPromptIdAsc();
}
