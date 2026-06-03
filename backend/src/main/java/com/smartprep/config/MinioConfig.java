package com.smartprep.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;

@Configuration
@Slf4j
public class MinioConfig {

    @Value("${app.minio.endpoint}")
    private String endpoint;

    @Value("${app.minio.access-key}")
    private String accessKey;

    @Value("${app.minio.secret-key}")
    private String secretKey;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)  // Required for MinIO
                .build();
    }

    @PostConstruct
    public void initBucket() {
        try {
            S3Client client = s3Client();
            HeadBucketRequest headRequest = HeadBucketRequest.builder().bucket(bucket).build();
            try {
                client.headBucket(headRequest);
                log.info("MinIO bucket '{}' already exists", bucket);
            } catch (NoSuchBucketException e) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created MinIO bucket '{}'", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket '{}': {}. Audio storage may not work.", bucket, e.getMessage());
        }
    }
}
