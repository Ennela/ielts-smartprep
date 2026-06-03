package com.smartprep.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text-to-Speech service wrapping Microsoft Edge TTS API (via local Python microservice).
 * Supports multi-voice synthesis by assigning different neural voices to speakers.
 */
@Service
@Slf4j
public class TtsService {

    @Value("${app.tts.enabled:true}")
    private boolean ttsEnabled;

    @Value("${app.tts.edge-tts-url:http://localhost:8000}")
    private String edgeTtsUrl;

    @Value("${app.tts.speaking-rate:-8%}")
    private String speakingRate;

    private RestTemplate restTemplate;

    // Edge TTS Neural Voices (alternating male/female)
    private static final List<String> VOICE_POOL = List.of(
            "en-US-GuyNeural",     // US Male
            "en-US-AriaNeural",    // US Female
            "en-GB-RyanNeural",    // UK Male
            "en-GB-SoniaNeural",   // UK Female
            "en-AU-WilliamNeural", // AU Male
            "en-AU-NatashaNeural"  // AU Female
    );

    // Pattern to match speaker lines like "Sarah: some text here"
    private static final Pattern SPEAKER_LINE_PATTERN =
            Pattern.compile("^([A-Z][a-zA-Z.\\s]+?):\\s*(.+)$", Pattern.MULTILINE);

    @PostConstruct
    public void init() {
        if (!ttsEnabled) {
            log.info("Edge TTS is disabled via configuration");
            return;
        }
        restTemplate = new RestTemplate();
        log.info("Edge TTS service initialized. Target URL: {}", edgeTtsUrl);
    }

    /**
     * Check if TTS is available.
     */
    public boolean isAvailable() {
        return ttsEnabled;
    }

    /**
     * Synthesize a script into MP3 audio with multi-voice support.
     * Detects speakers in the script and assigns different neural voices.
     *
     * @param script the transcript text (may contain "Speaker: text" format)
     * @return MP3 audio bytes
     */
    public byte[] synthesizeMultiVoice(String script) {
        if (!isAvailable()) {
            throw new IllegalStateException("TTS service is not available");
        }

        List<SpeakerLine> speakerLines = parseSpeakers(script);

        if (speakerLines.size() == 1 && speakerLines.get(0).speakerName().equals("_NARRATOR_")) {
            // Monologue — single voice
            return synthesizeSingle(script, VOICE_POOL.get(1)); // Default to US female
        }

        // Multi-speaker: assign voice per speaker, synthesize each segment, concatenate
        Map<String, String> speakerVoices = assignVoices(speakerLines);
        List<byte[]> audioSegments = new ArrayList<>();

        for (SpeakerLine line : speakerLines) {
            String voice = speakerVoices.get(line.speakerName());
            byte[] segment = callTtsApi(line.text(), voice);
            audioSegments.add(segment);
        }

        return concatenateMp3Segments(audioSegments);
    }

    /**
     * Synthesize a plain text script with a single voice.
     */
    public byte[] synthesizeSingle(String text, String voice) {
        return callTtsApi(text, voice);
    }

    // ========== Speaker Parsing ==========

    /**
     * Parse a script into speaker lines.
     * Supports format: "SpeakerName: text here"
     * Groups consecutive lines from the same speaker.
     */
    List<SpeakerLine> parseSpeakers(String script) {
        List<SpeakerLine> result = new ArrayList<>();
        Matcher matcher = SPEAKER_LINE_PATTERN.matcher(script);

        int lastEnd = 0;
        String lastSpeaker = null;
        StringBuilder currentText = new StringBuilder();

        while (matcher.find()) {
            // Treat preamble as narrator
            if (lastEnd == 0 && matcher.start() > 0) {
                String preamble = script.substring(0, matcher.start()).trim();
                if (!preamble.isEmpty()) {
                    result.add(new SpeakerLine("_NARRATOR_", preamble));
                }
            }

            String speaker = matcher.group(1).trim();
            String text = matcher.group(2).trim();

            if (speaker.equals(lastSpeaker)) {
                currentText.append(" ").append(text);
            } else {
                if (lastSpeaker != null && !currentText.isEmpty()) {
                    result.add(new SpeakerLine(lastSpeaker, currentText.toString()));
                }
                lastSpeaker = speaker;
                currentText = new StringBuilder(text);
            }

            lastEnd = matcher.end();
        }

        // Flush last speaker
        if (lastSpeaker != null && !currentText.isEmpty()) {
            result.add(new SpeakerLine(lastSpeaker, currentText.toString()));
        }

        // If no speaker pattern found, treat entire script as monologue
        if (result.isEmpty()) {
            result.add(new SpeakerLine("_NARRATOR_", script.trim()));
        }

        return result;
    }

    // ========== Voice Assignment ==========

    private Map<String, String> assignVoices(List<SpeakerLine> lines) {
        Map<String, String> voiceMap = new LinkedHashMap<>();
        int voiceIndex = 0;

        for (SpeakerLine line : lines) {
            if (!voiceMap.containsKey(line.speakerName())) {
                voiceMap.put(line.speakerName(), VOICE_POOL.get(voiceIndex % VOICE_POOL.size()));
                voiceIndex++;
            }
        }

        log.debug("Voice assignment: {}", voiceMap);
        return voiceMap;
    }

    // ========== HTTP TTS Service Call ==========

    private byte[] callTtsApi(String text, String voice) {
        // Build URL with UriComponentsBuilder.
        // Use build(true) to indicate values are already encoded, preventing double-encoding.
        // Rate (e.g. "-8%") and text are URL-encoded manually so they survive correctly.
        String encodedText = java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);
        String encodedVoice = java.net.URLEncoder.encode(voice, java.nio.charset.StandardCharsets.UTF_8);
        // Encode rate: -8% → -8%25 — FastAPI will URL-decode it back to -8%
        String encodedRate = java.net.URLEncoder.encode(speakingRate, java.nio.charset.StandardCharsets.UTF_8);

        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(edgeTtsUrl)
                .path("/synthesize")
                .query("text=" + encodedText + "&voice=" + encodedVoice + "&rate=" + encodedRate)
                .build(true)  // treat values as already encoded
                .toUri();

        try {
            log.debug("Calling Edge TTS: voice={}, textLength={}, rate={}, url={}",
                    voice, text.length(), speakingRate, uri);
            byte[] response = restTemplate.getForObject(uri, byte[].class);
            if (response == null || response.length == 0) {
                throw new RuntimeException("Empty response received from Edge TTS service");
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to connect to Edge TTS microservice: {}", e.getMessage());
            throw new RuntimeException("Edge TTS synthesis failed", e);
        }
    }

    // ========== MP3 Concatenation ==========

    byte[] concatenateMp3Segments(List<byte[]> segments) {
        int totalSize = segments.stream().mapToInt(s -> s.length).sum();
        byte[] result = new byte[totalSize];
        int offset = 0;

        for (byte[] segment : segments) {
            System.arraycopy(segment, 0, result, offset, segment.length);
            offset += segment.length;
        }

        log.info("Concatenated {} MP3 segments into {}KB audio", segments.size(), totalSize / 1024);
        return result;
    }

    public record SpeakerLine(String speakerName, String text) {}
}
