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

    private static final String OCI_REGION = "ap-chuncheon-1";
    private static final String OCI_NAMESPACE = "axkrsogyxb64";
    private static final String OCI_BUCKET = "drone-gallery";

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
