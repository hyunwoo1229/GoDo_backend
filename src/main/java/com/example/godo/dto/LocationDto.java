package com.example.godo.dto;

import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository.LocationProjection;

public record LocationDto(
        Long id,
        Double latitude,
        Double longitude,
        MediaType mediaType,
        String thumbnailUrl
) {

    public static LocationDto from(LocationProjection projection) {
        return new LocationDto(
                projection.getId(),
                projection.getLatitude(),
                projection.getLongitude(),
                projection.getMediaType(),
                projection.getThumbnailUrl()
        );
    }
}
