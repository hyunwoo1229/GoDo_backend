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

    public static MediaResponse from(Media media) {
        return new MediaResponse(
                media.getId(),
                media.getOriginalFileName(),
                media.getFileUrl(),
                media.getThumbnailUrl(),
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
