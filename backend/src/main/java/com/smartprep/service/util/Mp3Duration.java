package com.smartprep.service.util;

/**
 * The playing time of a constant-bitrate MP3, which is what the edge-tts sidecar produces
 * (24 kHz, 48 kbit/s mono): the bitrate from the first Layer III frame header, applied to
 * the bytes from that frame on.
 */
public final class Mp3Duration {

    // kbit/s by bitrate index, Layer III
    private static final int[] MPEG1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320};
    private static final int[] MPEG2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160};

    private Mp3Duration() {
    }

    /** Whole seconds, or null when the bytes hold no MPEG Layer III frame header. */
    public static Integer seconds(byte[] mp3) {
        if (mp3 == null) {
            return null;
        }
        int start = 0;
        if (mp3.length >= 10 && mp3[0] == 'I' && mp3[1] == 'D' && mp3[2] == '3') {
            // ID3v2 tag: its size is a 28-bit synchsafe integer in bytes 6..9
            start = 10 + (((mp3[6] & 0x7F) << 21) | ((mp3[7] & 0x7F) << 14)
                    | ((mp3[8] & 0x7F) << 7) | (mp3[9] & 0x7F));
        }
        for (int i = start; i + 2 < mp3.length; i++) {
            if ((mp3[i] & 0xFF) != 0xFF || (mp3[i + 1] & 0xE0) != 0xE0) {
                continue;
            }
            int version = (mp3[i + 1] >> 3) & 0x3;      // 3 MPEG-1, 2 MPEG-2, 0 MPEG-2.5, 1 reserved
            int layer = (mp3[i + 1] >> 1) & 0x3;        // 1 Layer III
            int bitrateIndex = (mp3[i + 2] >> 4) & 0xF; // 0 free format, 15 invalid
            if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15) {
                continue;
            }
            int kbps = (version == 3 ? MPEG1 : MPEG2)[bitrateIndex];
            return (int) Math.round((mp3.length - i) * 8.0 / (kbps * 1000.0));
        }
        return null;
    }
}
