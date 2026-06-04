package com.smartprep.service;

import com.smartprep.dto.response.WritingPromptResponse;
import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.WritingPrompt;
import com.smartprep.model.enums.EssayType;
import com.smartprep.repository.WritingPromptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt listing and lookup for Writing.
 * Extracted from the original WritingService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
public class WritingPromptService {

    private final WritingPromptRepository promptRepository;

    @Transactional(readOnly = true)
    public List<WritingPromptResponse> getPrompts(String essayTypeFilter) {
        List<WritingPrompt> prompts;
        if (essayTypeFilter != null && !essayTypeFilter.isBlank()) {
            EssayType type = EssayType.valueOf(essayTypeFilter.toUpperCase().trim());
            prompts = promptRepository.findByEssayTypeOrderByCreatedAtDesc(type);
        } else {
            prompts = promptRepository.findAllByOrderByCreatedAtDesc();
        }
        return prompts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WritingPromptResponse getPromptById(Long promptId) {
        WritingPrompt prompt = promptRepository.findById(promptId)
                .orElseThrow(() -> new ResourceNotFoundException("Writing prompt not found"));
        return toResponse(prompt);
    }

    private WritingPromptResponse toResponse(WritingPrompt p) {
        return WritingPromptResponse.builder()
                .promptId(p.getPromptId())
                .promptText(p.getPromptText())
                .essayType(p.getEssayType().name())
                .imageUrl(p.getImageUrl())
                .build();
    }
}
