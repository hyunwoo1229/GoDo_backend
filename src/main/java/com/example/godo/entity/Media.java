package com.example.godo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

@Entity
@Table(name = "media", indexes = {
        @Index(name = "idx_media_location", columnList = "latitude, longitude"),
        @Index(name = "idx_media_status", columnList = "status")
})
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "media_seq_gen")
    @SequenceGenerator(name = "media_seq_gen", sequenceName = "media_seq", allocationSize = 1)
    private Long id;

    @Column(name = "original_file_name", length = 200, nullable = false)
    private String originalFileName;

    @Column(name = "s3_key", length = 500, nullable = false, unique = true)
    private String s3Key;

    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "latitude", nullable = true)
    private Double latitude;

    @Column(name = "longitude", nullable = true)
    private Double longitude;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MediaStatus status;

    @Column(name = "display_order")
    private Integer displayOrder;

    private Media(String originalFileName, String s3Key, String fileUrl,
                  MediaType mediaType, Long fileSize, String contentType) {
        this.originalFileName = originalFileName;
        this.s3Key = s3Key;
        this.fileUrl = fileUrl;
        this.mediaType = mediaType;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.status = MediaStatus.UPLOADING;
    }

    public static Media createUploading(String originalFileName, String s3Key, String fileUrl,
                                        MediaType mediaType, Long fileSize, String contentType) {
        return new Media(originalFileName, s3Key, fileUrl, mediaType, fileSize, contentType);
    }

    public void markAsReady() {
        this.status = MediaStatus.READY;
    }

    public void markAsFailed() {
        this.status = MediaStatus.FAILED;
    }

    public void updateLocation(Double latitude, Double longitude, String locationName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void updateCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public void updateDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
