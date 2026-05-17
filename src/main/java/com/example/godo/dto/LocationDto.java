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

    private static final String OCI_REGION = "YOUR_REGION";
    private static final String OCI_NAMESPACE = "YOUR_NAMESPACE";
    private static final String OCI_BUCKET = "YOUR_BUCKET";

    public static LocationDto from(LocationProjection projection) {
        String thumbnailKey = projection.getThumbnailUrl();
        return new LocationDto(
                projection.getId(),
                projection.getLatitude(),
                projection.getLongitude(),
                projection.getMediaType(),
                thumbnailKey != null ? buildNativeUrl(thumbnailKey) : null
        );
    }

    private static String buildNativeUrl(String objectKey) {
        return "https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s".formatted(
                OCI_REGION, OCI_NAMESPACE, OCI_BUCKET, objectKey);
    }
}
