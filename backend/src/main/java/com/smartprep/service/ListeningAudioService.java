package com.smartprep.service;

import com.smartprep.exception.ResourceNotFoundException;
import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.repository.ListeningPartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Audio orchestration for Listening parts: trigger TTS, manage audio status.
 * Extracted from the original ListeningService (SRP refactor).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ListeningAudioService {

    private final ListeningPartRepository partRepository;
    private final TtsService ttsService;
    private final AudioGenerationService audioGenerationService;

    private static final Pattern ANS_MARKER_PATTERN = Pattern.compile("\\[ANS_\\d+]|\\[/ANS_\\d+]");

    @Transactional
    public void generateAudio(Long partId) {
        ListeningPart part = partRepository.findById(partId)
                .orElseThrow(() -> new ResourceNotFoundException("Listening part not found: " + partId));

        if (part.getAudioStatus() == AudioStatus.READY) {
            log.info("Audio already generated for part {}", partId);
            return;
        }
        if (!ttsService.isAvailable()) {
            throw new IllegalStateException("TTS service is not available");
        }
        audioGenerationService.generateAudioAsync(partId);
    }

    public void generateAudioAsync(Long partId) {
        audioGenerationService.generateAudioAsync(partId);
    }

    /**
     * Remove [ANS_X] and [/ANS_X] markers from script, keeping the text inside.
     */
    public String cleanScript(String script) {
        if (script == null) return "";
        return ANS_MARKER_PATTERN.matcher(script).replaceAll("").trim();
    }
}
