package com.shopee.monolith.modules.media.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.media.storage", name = "type", havingValue = "r2")
public class R2StorageService implements StorageService {

    @Value("${app.media.storage.r2.endpoint}")
    private String endpoint;

    @Value("${app.media.storage.r2.access-key-id}")
    private String accessKeyId;

    @Value("${app.media.storage.r2.secret-access-key}")
    private String secretAccessKey;

    @Value("${app.media.storage.r2.bucket}")
    private String bucket;

    @Value("${app.media.storage.r2.public-base-url}")
    private String publicBaseUrl;

    private S3Client s3Client;

    @PostConstruct
    void initialize() {
        requireConfigured(endpoint, "R2 endpoint");
        requireConfigured(accessKeyId, "R2 access key id");
        requireConfigured(secretAccessKey, "R2 secret access key");
        requireConfigured(bucket, "R2 bucket");
        requireConfigured(publicBaseUrl, "R2 public base URL");

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .forcePathStyle(true)
                .build();
    }

    @Override
    public String store(byte[] bytes, String objectKey, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return objectKey;
    }

    @Override
    public String getPublicUrl(String objectKey) {
        return publicBaseUrl.replaceAll("/+$", "") + "/" + objectKey;
    }

    @Override
    public byte[] load(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    @Override
    public void delete(String objectKey) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            s3Client.deleteObject(request);
        } catch (RuntimeException e) {
            log.warn("[R2Storage] Failed to delete {}: {}", objectKey, e.getMessage());
        }
    }

    private void requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when app.media.storage.type=r2");
        }
    }
}
