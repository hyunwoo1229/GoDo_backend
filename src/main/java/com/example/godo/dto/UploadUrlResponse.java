package com.example.godo.dto;

public record UploadUrlResponse(
        Long mediaId,
        String presignedUrl,
        String s3Key,
        Long expiresIn
) {
}
