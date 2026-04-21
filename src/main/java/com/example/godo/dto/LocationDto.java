package com.example.godo.dto;

import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository.LocationProjection;
import com.example.godo.service.StorageService;

import java.time.Duration;

public record LocationDto(
        Long id,
        Double latitude,
        Double longitude,
        MediaType mediaType,
        String thumbnailUrl
) {

    private static final Duration DOWNLOAD_URL_EXPIRATION = Duration.ofHours(1);

    public static LocationDto from(LocationProjection projection, StorageService storageService) {
        String thumbnailUrl = projection.getThumbnailUrl() != null
                ? storageService.generatePresignedDownloadUrl(
                        projection.getThumbnailUrl(), DOWNLOAD_URL_EXPIRATION)
                : null;

        return new LocationDto(
                projection.getId(),
                projection.getLatitude(),
                projection.getLongitude(),
                projection.getMediaType(),
                thumbnailUrl
        );
    }
}
