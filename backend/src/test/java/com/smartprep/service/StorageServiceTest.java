package com.smartprep.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucket", "listening-audio");
        ReflectionTestUtils.setField(storageService, "endpoint", "http://localhost:9000");
    }

    @Test
    @DisplayName("uploadAudio: uploads to S3 and returns URL")
    void uploadAudio_success_returnsUrl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] mp3Data = new byte[]{1, 2, 3, 4, 5};
        String url = storageService.uploadAudio("test_part_1.mp3", mp3Data);

        assertNotNull(url);
        assertTrue(url.contains("test_part_1.mp3"));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("getAudioUrl: constructs correct proxy URL")
    void getAudioUrl_constructsCorrectUrl() {
        String url = storageService.getAudioUrl("part_42_123456.mp3");

        assertEquals("/api/v1/listening/audio/part_42_123456.mp3", url);
    }

    @Test
    @DisplayName("downloadAudio: downloads bytes from S3")
    @SuppressWarnings("unchecked")
    void downloadAudio_success_returnsBytes() {
        byte[] expectedBytes = {10, 20, 30};
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), expectedBytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = storageService.downloadAudio("part_1.mp3");

        assertArrayEquals(expectedBytes, result);
        verify(s3Client).getObjectAsBytes(any(GetObjectRequest.class));
    }
}
