package com.example.godo.service;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

public interface StorageService {

    String generatePresignedUploadUrl(String key, String contentType, Duration expiration);

    void uploadFile(String key, InputStream inputStream, long size, String contentType);

    void downloadToFile(String key, Path destinationPath);

    void deleteFile(String key);

    boolean doesObjectExist(String key);
}
