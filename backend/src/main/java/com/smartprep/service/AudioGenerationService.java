package com.smartprep.service;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.repository.ListeningPartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AudioGenerationService {

    private final ListeningPartRepository partRepository;
    private final TtsService ttsService;
    private final StorageService storageService;

    /**
     * Asynchronously generate TTS audio for a listening part.
     * Sets status to PENDING, calls TTS, uploads to MinIO, sets to READY (or FAILED if error).
     */
    @Async("ttsExecutor")
    @Transactional
    public void generateAudioAsync(Long partId) {
        // Step 1: Set state to PENDING and save to DB
        try {
            ListeningPart part = partRepository.findById(partId).orElse(null);
            if (part == null) {
                log.error("Listening part {} not found for audio generation", partId);
                return;
            }
            part.setAudioStatus(AudioStatus.PENDING);
            partRepository.saveAndFlush(part);
        } catch (Exception e) {
            log.error("Failed to set audio status to PENDING for part {}: {}", partId, e.getMessage());
        }

        // Step 2: Generate audio and upload
        try {
            ListeningPart part = partRepository.findById(partId)
                    .orElseThrow(() -> new IllegalArgumentException("Part not found: " + partId));

            String transcriptText = part.getTranscriptText();
            if (transcriptText == null || transcriptText.trim().isEmpty()) {
                throw new IllegalArgumentException("Transcript text is empty, cannot generate audio.");
            }

            String cleanScript = cleanScript(transcriptText);
            log.info("Generating TTS audio asynchronously for part {} ({} chars)", partId, cleanScript.length());

            if (!ttsService.isAvailable()) {
                throw new IllegalStateException("TTS Service is currently disabled/unavailable");
            }

            byte[] mp3Bytes = ttsService.synthesizeMultiVoice(cleanScript);

            String key = "part_" + partId + "_" + System.currentTimeMillis() + ".mp3";
            String audioUrl = storageService.uploadAudio(key, mp3Bytes);

            part.setAudioUrl(audioUrl);
            part.setAudioStatus(AudioStatus.READY);
            partRepository.save(part);

            log.info("TTS audio successfully generated and saved for part {}: {}", partId, audioUrl);
        } catch (Exception e) {
            log.error("Failed to generate TTS audio for part {}: {}", partId, e.getMessage(), e);
            try {
                ListeningPart part = partRepository.findById(partId).orElse(null);
                if (part != null) {
                    part.setAudioStatus(AudioStatus.FAILED);
                    partRepository.save(part);
                }
            } catch (Exception ex) {
                log.error("Failed to set audio status to FAILED for part {}: {}", partId, ex.getMessage());
            }
        }
    }

    private String cleanScript(String script) {
        if (script == null) return "";
        return script.replaceAll("\\[ANS_\\d+\\]|\\[/ANS_\\d+\\]", "").trim();
    }
}
