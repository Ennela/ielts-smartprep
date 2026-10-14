package com.smartprep.service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Mp3DurationTest {

    /** {@code length} bytes starting with one frame header (the rest is payload). */
    static byte[] mp3(int length, int headerByte1, int headerByte2) {
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) headerByte1;
        bytes[2] = (byte) headerByte2;
        return bytes;
    }

    @Test
    @DisplayName("edge-tts output (MPEG-2 Layer III, 48 kbit/s): 6000 bytes a second")
    void edgeTtsFormat() {
        // 0xF3: MPEG-2, Layer III, no CRC. 0x64: bitrate index 6 (48 kbit/s), 24 kHz.
        assertEquals(205, Mp3Duration.seconds(mp3(1_229_760, 0xF3, 0x64)));
        assertEquals(1, Mp3Duration.seconds(mp3(6_000, 0xF3, 0x64)));
    }

    @Test
    @DisplayName("MPEG-1 Layer III at 128 kbit/s: 16000 bytes a second")
    void mpeg1() {
        // 0xFB: MPEG-1, Layer III, no CRC. 0x90: bitrate index 9 (128 kbit/s), 44.1 kHz.
        assertEquals(2, Mp3Duration.seconds(mp3(32_000, 0xFB, 0x90)));
    }

    @Test
    @DisplayName("an ID3v2 tag at the start is skipped and not counted")
    void skipsId3() {
        byte[] audio = mp3(12_000, 0xF3, 0x64);
        byte[] tagged = new byte[10 + 20 + audio.length];
        tagged[0] = 'I'; tagged[1] = 'D'; tagged[2] = '3';
        tagged[9] = 20; // tag body of 20 bytes, filled with 0xFF that must not be read as a frame
        java.util.Arrays.fill(tagged, 10, 30, (byte) 0xFF);
        System.arraycopy(audio, 0, tagged, 30, audio.length);

        assertEquals(2, Mp3Duration.seconds(tagged));
    }

    @Test
    @DisplayName("bytes with no Layer III frame header give no duration")
    void noFrame() {
        assertNull(Mp3Duration.seconds(new byte[]{1, 2, 3}));
        assertNull(Mp3Duration.seconds(null));
        assertNull(Mp3Duration.seconds(mp3(6_000, 0xF3, 0xF4))); // bitrate index 15 is invalid
    }
}
