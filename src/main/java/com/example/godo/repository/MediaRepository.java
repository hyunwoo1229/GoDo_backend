package com.example.godo.repository;

import com.example.godo.entity.Media;
import com.example.godo.entity.MediaStatus;
import com.example.godo.entity.MediaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findAllByStatus(MediaStatus status);

    @Query("""
            SELECT m FROM Media m
            WHERE m.status = 'READY'
              AND m.latitude BETWEEN :minLat AND :maxLat
              AND m.longitude BETWEEN :minLng AND :maxLng
            ORDER BY COALESCE(m.capturedAt, m.uploadedAt) DESC
            """)
    Page<Media> findNearby(Double minLat, Double maxLat,
                           Double minLng, Double maxLng, Pageable pageable);

    @Query("""
            SELECT m.id as id, m.latitude as latitude, m.longitude as longitude,
                   m.mediaType as mediaType, m.thumbnailUrl as thumbnailUrl
            FROM Media m WHERE m.status = 'READY'
            """)
    List<LocationProjection> findAllReadyLocations();

    interface LocationProjection {
        Long getId();
        Double getLatitude();
        Double getLongitude();
        MediaType getMediaType();
        String getThumbnailUrl();
    }
}
