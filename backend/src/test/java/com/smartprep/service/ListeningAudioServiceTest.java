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

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListeningAudioServiceTest {

    @Mock private ListeningPartRepository partRepository;
    @Mock private TtsService ttsService;
    @Mock private AudioGenerationService audioGenerationService;

    @InjectMocks private ListeningAudioService listeningAudioService;

    private static ListeningPart pending(long id, String transcript) {
        return ListeningPart.builder().partId(id).transcriptText(transcript).audioStatus(AudioStatus.PENDING).build();
    }

    @Test
    @DisplayName("on startup, voices each pending part that has a transcript")
    void generatePendingAudio_voicesPartsWithTranscript() {
        when(ttsService.isAvailable()).thenReturn(true);
        when(partRepository.findByAudioStatus(AudioStatus.PENDING)).thenReturn(List.of(
                pending(1L, "Receptionist: Good morning."),
                pending(2L, null),
                pending(3L, "   "),
                pending(4L, "Lecturer: Today we look at sleep.")));

        listeningAudioService.generatePendingAudio();

        verify(audioGenerationService).generateAudioAsync(1L);
        verify(audioGenerationService).generateAudioAsync(4L);
        verify(audioGenerationService, never()).generateAudioAsync(2L);
        verify(audioGenerationService, never()).generateAudioAsync(3L);
    }

    @Test
    @DisplayName("on startup, does nothing when TTS is switched off")
    void generatePendingAudio_ttsOff_doesNothing() {
        when(ttsService.isAvailable()).thenReturn(false);

        listeningAudioService.generatePendingAudio();

        verifyNoInteractions(partRepository);
        verify(audioGenerationService, never()).generateAudioAsync(anyLong());
    }
}
