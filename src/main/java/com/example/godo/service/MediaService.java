package com.example.godo.service;

import com.example.godo.config.StorageProperties;
import com.example.godo.dto.CompleteUploadRequest;
import com.example.godo.dto.LocationDto;
import com.example.godo.dto.MediaResponse;
import com.example.godo.dto.ReorderItem;
import com.example.godo.dto.UploadUrlRequest;
import com.example.godo.dto.UploadUrlResponse;
import com.example.godo.entity.Media;
import com.example.godo.entity.MediaStatus;
import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final VideoConversionService videoConversionService;
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
    @Caching(evict = {
            @CacheEvict(value = "media:gallery", allEntries = true),
            @CacheEvict(value = "media:locations", allEntries = true),
            @CacheEvict(value = "media:byId", key = "#mediaId"),
            @CacheEvict(value = "media:byLocation", allEntries = true)
    })
    public MediaResponse completeUpload(Long mediaId, CompleteUploadRequest request) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + mediaId));

        if (media.getStatus() != MediaStatus.UPLOADING) {
            throw new IllegalStateException(
                    "Media is not in UPLOADING state: id=" + mediaId + ", status=" + media.getStatus());
        }

        media.updateLocation(request.latitude(), request.longitude(), request.locationName());
        if (request.capturedAt() != null) {
            media.updateCapturedAt(request.capturedAt());
        }
        media.markAsReady();
        log.info("Media {} marked as READY", mediaId);

        if (media.getMediaType() == MediaType.VIDEO) {
            scheduleThumbnailAfterCommit(media.getId());
        }

        return MediaResponse.from(media, storageService);
    }

    // 썸네일 생성은 부모 트랜잭션이 커밋된 후에만 실행되어야 한다.
    // 그렇지 않으면 비동기 트랜잭션이 status=UPLOADING 스냅샷을 본 채로
    // 마지막에 커밋하며 부모의 status=READY 변경을 덮어쓸 수 있다.
    private void scheduleThumbnailAfterCommit(Long mediaId) {
        Runnable trigger = () -> {
            try {
                thumbnailService.generateThumbnailAsync(mediaId);
                log.info("Thumbnail generation requested for mediaId={}", mediaId);
            } catch (Exception e) {
                log.warn("Thumbnail generation request failed for mediaId={}, Media is still READY",
                        mediaId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "media:gallery", allEntries = true),
            @CacheEvict(value = "media:locations", allEntries = true),
            @CacheEvict(value = "media:byId", key = "#mediaId"),
            @CacheEvict(value = "media:byLocation", allEntries = true)
    })
    public MediaResponse uploadComplete(Long mediaId, CompleteUploadRequest request) {
        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + mediaId));

        if (media.getStatus() != MediaStatus.UPLOADING) {
            throw new IllegalStateException(
                    "Media is not in UPLOADING state: id=" + mediaId + ", status=" + media.getStatus());
        }

        media.updateLocation(request.latitude(), request.longitude(), request.locationName());
        if (request.capturedAt() != null) {
            media.updateCapturedAt(request.capturedAt());
        }

        if (media.getMediaType() == MediaType.VIDEO) {
            // 비디오는 변환 파이프라인이 status를 CONVERTING → READY/FAILED로 전이시킨다.
            scheduleConversionAfterCommit(mediaId);
            log.info("Media {} queued for WebM conversion", mediaId);
        } else {
            media.markAsReady();
            log.info("Media {} marked as READY (image, no conversion)", mediaId);
        }

        return MediaResponse.from(media, storageService);
    }

    private void scheduleConversionAfterCommit(Long mediaId) {
        Runnable trigger = () -> {
            try {
                videoConversionService.convertToWebM(mediaId);
            } catch (Exception e) {
                log.warn("Conversion submission failed for mediaId={}", mediaId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trigger.run();
                }
            });
        } else {
            trigger.run();
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "media:gallery", key = "#page + ':' + #size")
    public List<MediaResponse> getAllMedia(int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "displayOrder")
                        .and(Sort.by(Sort.Direction.DESC, "uploadedAt")));
        return mediaRepository.findAllByStatus(MediaStatus.READY, pageable).stream()
                .map(media -> MediaResponse.from(media, storageService))
                .toList();
    }

    @Transactional
    @CacheEvict(value = "media:gallery", allEntries = true)
    public void reorderMedia(List<ReorderItem> items) {
        for (ReorderItem item : items) {
            mediaRepository.findById(item.id())
                    .ifPresent(media -> media.updateDisplayOrder(item.displayOrder()));
        }
    }

    @Transactional(readOnly = true)
    @Cacheable("media:locations")
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
    @Cacheable(value = "media:byId", key = "#id")
    public MediaResponse getMedia(Long id) {
        Media media = mediaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Media not found: id=" + id));
        return MediaResponse.from(media, storageService);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "media:gallery", allEntries = true),
            @CacheEvict(value = "media:locations", allEntries = true),
            @CacheEvict(value = "media:byId", key = "#mediaId"),
            @CacheEvict(value = "media:byLocation", allEntries = true)
    })
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
