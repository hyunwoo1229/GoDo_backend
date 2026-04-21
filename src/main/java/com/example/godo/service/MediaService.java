package com.example.godo.service;

import com.example.godo.config.StorageProperties;
import com.example.godo.dto.CompleteUploadRequest;
import com.example.godo.dto.LocationDto;
import com.example.godo.dto.MediaResponse;
import com.example.godo.dto.UploadUrlRequest;
import com.example.godo.dto.UploadUrlResponse;
import com.example.godo.entity.Media;
import com.example.godo.entity.MediaStatus;
import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final long IMAGE_MAX_SIZE = 10L * 1024 * 1024;
    private static final long VIDEO_MAX_SIZE = 500L * 1024 * 1024;
    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofMinutes(10);

    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final ThumbnailService thumbnailService;
    private final StorageProperties storageProperties;

    @Transactional
    public UploadUrlResponse createUploadUrl(UploadUrlRequest request) {
        validateFileSize(request.mediaType(), request.fileSize());

        String s3Key = buildS3Key(request.fileName());
        String fileUrl = "%s/%s/%s".formatted(
                storageProperties.getEndpoint(), storageProperties.getBucket(), s3Key);

        Media media = Media.createUploading(
                request.fileName(),
                s3Key,
                fileUrl,
                request.mediaType(),
                request.fileSize(),
                request.contentType()
        );
        Media saved = mediaRepository.save(media);

        String presignedUrl = storageService.generatePresignedUploadUrl(
                s3Key, request.contentType(), PRESIGNED_URL_EXPIRATION);

        log.info("Created upload URL for mediaId={}, s3Key={}", saved.getId(), s3Key);

        return new UploadUrlResponse(
                saved.getId(),
                presignedUrl,
                s3Key,
                PRESIGNED_URL_EXPIRATION.getSeconds()
        );
    }

    @Transactional
    public MediaResponse completeUpload(Long mediaId, CompleteUploadRequest request) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + mediaId));

        if (media.getStatus() != MediaStatus.UPLOADING) {
            throw new IllegalStateException(
                    "Media is not in UPLOADING state: id=" + mediaId + ", status=" + media.getStatus());
        }

        if (!storageService.doesObjectExist(media.getS3Key())) {
            media.markAsFailed();
            log.warn("Upload completion failed — object not found: mediaId={}, s3Key={}",
                    mediaId, media.getS3Key());
            throw new IllegalStateException("Uploaded object not found for mediaId=" + mediaId);
        }

        media.updateLocation(request.latitude(), request.longitude(), request.locationName());
        if (request.capturedAt() != null) {
            media.updateCapturedAt(request.capturedAt());
        }
        media.markAsReady();

        if (media.getMediaType() == MediaType.VIDEO) {
            thumbnailService.generateThumbnailAsync(media.getId());
        }

        log.info("Completed upload for mediaId={}", mediaId);
        return MediaResponse.from(media, storageService);
    }

    @Transactional(readOnly = true)
    public List<LocationDto> getAllLocations() {
        return mediaRepository.findAllReadyLocations().stream()
                .map(projection -> LocationDto.from(projection, storageService))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MediaResponse> getNearbyMedia(Double lat, Double lng, Double radius, Pageable pageable) {
        double effectiveRadius = radius != null ? radius : 0.001;
        double minLat = lat - effectiveRadius;
        double maxLat = lat + effectiveRadius;
        double minLng = lng - effectiveRadius;
        double maxLng = lng + effectiveRadius;

        return mediaRepository.findNearby(minLat, maxLat, minLng, maxLng, pageable)
                .map(media -> MediaResponse.from(media, storageService));
    }

    @Transactional(readOnly = true)
    public MediaResponse getMedia(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + id));
        return MediaResponse.from(media, storageService);
    }

    @Transactional
    public void deleteMedia(Long mediaId) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + mediaId));

        storageService.deleteFile(media.getS3Key());
        if (media.getThumbnailUrl() != null) {
            storageService.deleteFile(media.getThumbnailUrl());
        }

        mediaRepository.delete(media);
        log.info("Deleted mediaId={}", mediaId);
    }

    private void validateFileSize(MediaType mediaType, long fileSize) {
        long maxSize = mediaType == MediaType.IMAGE ? IMAGE_MAX_SIZE : VIDEO_MAX_SIZE;
        if (fileSize > maxSize) {
            throw new IllegalArgumentException(
                    "File size exceeds limit for %s: size=%d, max=%d".formatted(mediaType, fileSize, maxSize));
        }
    }

    private String buildS3Key(String originalFileName) {
        LocalDate today = LocalDate.now();
        String extension = extractExtension(originalFileName);
        return "uploads/%d/%02d/%02d/%s.%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }
}
