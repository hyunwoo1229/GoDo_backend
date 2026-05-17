package com.example.godo.dto;

import com.example.godo.entity.Media;
import com.example.godo.entity.MediaStatus;
import com.example.godo.entity.MediaType;

import java.time.LocalDateTime;

public record MediaResponse(
        Long id,
        String originalFileName,
        String fileUrl,
        String thumbnailUrl,
        MediaType mediaType,
        Double latitude,
        Double longitude,
        String locationName,
        LocalDateTime capturedAt,
        LocalDateTime uploadedAt,
        MediaStatus status
) {

    private static final String OCI_REGION = "YOUR_REGION";
    private static final String OCI_NAMESPACE = "YOUR_NAMESPACE";
    private static final String OCI_BUCKET = "YOUR_BUCKET";

    public static MediaResponse from(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getOriginalFileName(),
                buildNativeUrl(media.getS3Key()),
                media.getThumbnailUrl() != null ? buildNativeUrl(media.getThumbnailUrl()) : null,
                media.getMediaType(),
                media.getLatitude(),
                media.getLongitude(),
                media.getLocationName(),
                media.getCapturedAt(),
                media.getUploadedAt(),
                media.getStatus()
        );
    }

    private static String buildNativeUrl(String objectKey) {
        return "https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s".formatted(
                OCI_REGION, OCI_NAMESPACE, OCI_BUCKET, objectKey);
    }
}
