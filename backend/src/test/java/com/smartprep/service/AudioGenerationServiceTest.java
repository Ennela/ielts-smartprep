package com.smartprep.service;

import com.smartprep.model.entity.ListeningPart;
import com.smartprep.model.enums.AudioStatus;
import com.smartprep.repository.ListeningPartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AudioGenerationServiceTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private TtsService ttsService;
    @Mock private StorageService storageService;

    @InjectMocks private AudioGenerationService audioGenerationService;

    @Test
    @DisplayName("generateAudioAsync: success updates status to READY")
    void generateAudioAsync_success_updatesStatusToReady() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L)
                .transcriptText("Sarah: Hello there.\nJohn: Hi!")
                .audioStatus(AudioStatus.PENDING)
                .build();

        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(ttsService.isAvailable()).thenReturn(true);
        when(ttsService.synthesizeMultiVoice(anyString())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.uploadAudio(anyString(), any(byte[].class))).thenReturn("/api/v1/listening/audio/part_1.mp3");
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> inv.getArgument(0));

        audioGenerationService.generateAudioAsync(1L);

        verify(ttsService).synthesizeMultiVoice(anyString());
        verify(storageService).uploadAudio(anyString(), any(byte[].class));
        verify(partRepository).save(argThat(p ->
                p.getAudioStatus() == AudioStatus.READY &&
                p.getAudioUrl().contains("part_1")));
    }

    @Test
    @DisplayName("generateAudioAsync: the part's duration becomes the real length of the audio made")
    void generateAudioAsync_success_storesRealDuration() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L).partNumber(4)
                .transcriptText("A lecture.")
                .durationSeconds(420)   // the guess a generated part starts with
                .audioStatus(AudioStatus.PENDING)
                .build();
        // 12000 bytes of MPEG-2 Layer III at 48 kbit/s, as edge-tts sends: two seconds
        byte[] audio = new byte[12_000];
        audio[0] = (byte) 0xFF; audio[1] = (byte) 0xF3; audio[2] = (byte) 0x64;

        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(ttsService.isAvailable()).thenReturn(true);
        when(ttsService.synthesizeSingle(anyString(), anyString())).thenReturn(audio);
        when(storageService.uploadAudio(anyString(), any(byte[].class))).thenReturn("/api/v1/listening/audio/part_1.mp3");
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> inv.getArgument(0));

        audioGenerationService.generateAudioAsync(1L);

        verify(partRepository).save(argThat(p ->
                p.getAudioStatus() == AudioStatus.READY && p.getDurationSeconds() == 2));
    }

    @Test
    @DisplayName("generateAudioAsync: TTS failure sets status to FAILED")
    void generateAudioAsync_ttsFailure_statusSetToFailed() {
        ListeningPart part = ListeningPart.builder()
                .partId(1L)
                .transcriptText("Some script")
                .audioStatus(AudioStatus.PENDING)
                .build();

        when(partRepository.findById(1L)).thenReturn(Optional.of(part));
        when(ttsService.isAvailable()).thenReturn(true);
        when(ttsService.synthesizeMultiVoice(anyString())).thenThrow(new RuntimeException("TTS API error"));
        when(partRepository.save(any(ListeningPart.class))).thenAnswer(inv -> inv.getArgument(0));

        audioGenerationService.generateAudioAsync(1L);

        verify(partRepository, atLeastOnce()).save(argThat(p ->
                p.getAudioStatus() == AudioStatus.FAILED));
    }
}
