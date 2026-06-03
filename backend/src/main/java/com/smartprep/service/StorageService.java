package com.smartprep.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final S3Client s3Client;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.minio.endpoint}")
    private String endpoint;

    /**
     * Upload an MP3 audio file to MinIO.
     *
     * @param key      the object key (e.g., "part_42_1717412345.mp3")
     * @param mp3Data  the raw MP3 bytes
     * @return the public URL for the uploaded file
     */
    public String uploadAudio(String key, byte[] mp3Data) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("audio/mpeg")
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(mp3Data));
        log.info("Uploaded audio to MinIO: bucket={}, key={}, size={}KB", bucket, key, mp3Data.length / 1024);

        return getAudioUrl(key);
    }

    /**
     * Download audio bytes from MinIO.
     *
     * @param key the object key
     * @return the raw bytes of the audio file
     */
    public byte[] downloadAudio(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObjectAsBytes(getRequest).asByteArray();
    }

    /**
     * Construct the access URL for a given object key.
     * In production, this could be a CDN or pre-signed URL.
     */
    public String getAudioUrl(String key) {
        // Use the backend proxy endpoint so audio is served through our API
        return "/api/v1/listening/audio/" + key;
    }

    /**
     * Delete an audio file from MinIO.
     *
     * @param key the object key to delete
     */
    public void deleteAudio(String key) {
        try {
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest deleteRequest =
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();
            s3Client.deleteObject(deleteRequest);
            log.info("Deleted audio from MinIO: bucket={}, key={}", bucket, key);
        } catch (Exception e) {
            log.warn("Failed to delete audio from MinIO: bucket={}, key={}, error={}", bucket, key, e.getMessage());
        }
    }
}
