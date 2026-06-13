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
    public void generateAudioAsync(Long partId) {
        // Step 1: Set state to PENDING and load part details in a short operation
        String transcriptText = null;
        Integer partNumber = null;
        try {
            ListeningPart part = partRepository.findById(partId).orElse(null);
            if (part == null) {
                log.error("Listening part {} not found for audio generation", partId);
                return;
            }
            part.setAudioStatus(AudioStatus.PENDING);
            partRepository.saveAndFlush(part);
            transcriptText = part.getTranscriptText();
            partNumber = part.getPartNumber();
        } catch (Exception e) {
            log.error("Failed to set audio status to PENDING for part {}: {}", partId, e.getMessage());
            return;
        }

        // Step 2: Generate audio (external network call, running outside transaction)
        byte[] mp3Bytes = null;
        String cleanScript = cleanScript(transcriptText);
        try {
            if (transcriptText == null || transcriptText.trim().isEmpty()) {
                throw new IllegalArgumentException("Transcript text is empty, cannot generate audio.");
            }

            log.info("Generating TTS audio asynchronously for part {} ({} chars)", partId, cleanScript.length());

            if (!ttsService.isAvailable()) {
                throw new IllegalStateException("TTS Service is currently disabled/unavailable");
            }

            if (partNumber != null && (partNumber == 2 || partNumber == 4)) {
                // Part 2 & 4: single-voice monologue (presenter or lecturer)
                String voice = (partNumber == 4) ? "en-US-GuyNeural" : "en-US-AriaNeural";
                String textToSynthesize = stripSpeakerPrefixes(cleanScript);
                mp3Bytes = ttsService.synthesizeSingle(textToSynthesize, voice);
            } else {
                // Part 1 & 3: dialogue (multi-voice assignment)
                mp3Bytes = ttsService.synthesizeMultiVoice(cleanScript);
            }
        } catch (Exception e) {
            log.error("Failed to generate TTS audio for part {}: {}", partId, e.getMessage(), e);
            updateStatus(partId, AudioStatus.FAILED, null);
            return;
        }

        // Step 3: Upload and save URL
        try {
            String key = "part_" + partId + "_" + System.currentTimeMillis() + ".mp3";
            String audioUrl = storageService.uploadAudio(key, mp3Bytes);

            updateStatus(partId, AudioStatus.READY, audioUrl);
            log.info("TTS audio successfully generated and saved for part {}: {}", partId, audioUrl);
        } catch (Exception e) {
            log.error("Failed to upload audio or update status for part {}: {}", partId, e.getMessage(), e);
            updateStatus(partId, AudioStatus.FAILED, null);
        }
    }

    private void updateStatus(Long partId, AudioStatus status, String audioUrl) {
        try {
            ListeningPart part = partRepository.findById(partId).orElse(null);
            if (part != null) {
                part.setAudioStatus(status);
                if (audioUrl != null) {
                    part.setAudioUrl(audioUrl);
                }
                partRepository.save(part);
            }
        } catch (Exception ex) {
            log.error("Failed to update status to {} for part {}: {}", status, partId, ex.getMessage());
        }
    }

    private String stripSpeakerPrefixes(String script) {
        if (script == null) return "";
        return script.replaceAll("(?m)^[A-Za-z0-9\\s\\.\\-]+:\\s*", "");
    }

    private String cleanScript(String script) {
        if (script == null) return "";
        return script.replaceAll("\\[ANS_\\d+\\]|\\[/ANS_\\d+\\]", "").trim();
    }
}
