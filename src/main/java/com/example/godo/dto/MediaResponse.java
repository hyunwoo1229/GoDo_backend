package com.example.godo.dto;

import com.example.godo.entity.Media;
import com.example.godo.entity.MediaStatus;
import com.example.godo.entity.MediaType;
import com.example.godo.service.StorageService;

import java.time.Duration;
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

    private static final Duration DOWNLOAD_URL_EXPIRATION = Duration.ofHours(1);

    public static MediaResponse from(Media media, StorageService storageService) {
        String fileUrl = storageService.generatePresignedDownloadUrl(
                media.getS3Key(), DOWNLOAD_URL_EXPIRATION);
        String thumbnailUrl = media.getThumbnailUrl() != null
                ? storageService.generatePresignedDownloadUrl(
                        media.getThumbnailUrl(), DOWNLOAD_URL_EXPIRATION)
                : null;

        return new MediaResponse(
                media.getId(),
                media.getOriginalFileName(),
                fileUrl,
                thumbnailUrl,
                media.getMediaType(),
                media.getLatitude(),
                media.getLongitude(),
                media.getLocationName(),
                media.getCapturedAt(),
                media.getUploadedAt(),
                media.getStatus()
        );
    }
}
