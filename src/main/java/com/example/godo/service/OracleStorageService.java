package com.example.godo.service;

import com.example.godo.config.StorageProperties;
import com.example.godo.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OracleStorageService implements StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    @Override
    public String generatePresignedUploadUrl(String key, String contentType, Duration expiration) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .putObjectRequest(putRequest)
                    .build();

            String url = s3Presigner.presignPutObject(presignRequest).url().toString();
            log.info("Generated presigned upload URL for key={}, contentType={}, expiresIn={}s",
                    key, contentType, expiration.getSeconds());
            return url;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned upload URL for key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to generate presigned upload URL for key=" + key, e);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String key, Duration expiration) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.info("Generated presigned download URL for key={}, expiresIn={}s",
                    key, expiration.getSeconds());
            return url;
        } catch (S3Exception e) {
            log.error("Failed to generate presigned download URL for key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to generate presigned download URL for key=" + key, e);
        }
    }

    @Override
    public void uploadFile(String key, InputStream inputStream, long size, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(size)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, size));
            log.info("Uploaded object key={}, size={}B, contentType={}", key, size, contentType);
        } catch (S3Exception e) {
            log.error("Failed to upload object key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to upload object key=" + key, e);
        }
    }

    @Override
    public void downloadToFile(String key, Path destinationPath) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            s3Client.getObject(request, destinationPath);
            log.info("Downloaded object key={} to path={}", key, destinationPath);
        } catch (S3Exception e) {
            log.error("Failed to download object key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to download object key=" + key, e);
        }
    }

    @Override
    public void deleteFile(String key) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            s3Client.deleteObject(request);
            log.info("Deleted object key={}", key);
        } catch (S3Exception e) {
            log.error("Failed to delete object key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to delete object key=" + key, e);
        }
    }

    @Override
    public boolean doesObjectExist(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            log.error("Failed to check existence of key={}: {}", key, e.getMessage());
            throw new StorageException("Failed to check existence of key=" + key, e);
        }
    }
}
