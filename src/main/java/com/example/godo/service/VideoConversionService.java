package com.example.godo.service;

import com.example.godo.config.StorageProperties;
import com.example.godo.entity.Media;
import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoConversionService {

    private static final String TEMP_DIR = "/tmp/godo-conversion";
    private static final String CONVERTED_PREFIX = "converted/";
    private static final String THUMBNAIL_PREFIX = "thumbnails/";
    private static final int FFMPEG_TIMEOUT_MINUTES = 30;
    private static final int MAX_ATTEMPTS = 2;

    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final MediaCacheEvictor mediaCacheEvictor;
    private final TransactionTemplate transactionTemplate;

    @Async("videoTaskExecutor")
    public void convertToWebM(Long mediaId) {
        Optional<Media> maybeMedia = transactionTemplate.execute(status -> mediaRepository.findById(mediaId));
        if (maybeMedia == null || maybeMedia.isEmpty()) {
            log.warn("Video conversion skipped — media not found: id={}", mediaId);
            return;
        }
        if (maybeMedia.get().getMediaType() != MediaType.VIDEO) {
            log.debug("Video conversion skipped — not a video: id={}", mediaId);
            return;
        }

        markConverting(mediaId);

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                runConversionPipeline(mediaId);
                mediaCacheEvictor.evictAllForMedia(mediaId);
                log.info("Video conversion succeeded: mediaId={}, attempt={}", mediaId, attempt);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("Video conversion attempt {} failed for mediaId={}: {}",
                        attempt, mediaId, e.getMessage());
            }
        }

        markFailed(mediaId);
        mediaCacheEvictor.evictAllForMedia(mediaId);
        log.error("Video conversion permanently failed for mediaId={}", mediaId, lastError);
    }

    private void markConverting(Long mediaId) {
        transactionTemplate.executeWithoutResult(status ->
                mediaRepository.findById(mediaId).ifPresent(Media::markAsConverting));
    }

    private void markFailed(Long mediaId) {
        transactionTemplate.executeWithoutResult(status ->
                mediaRepository.findById(mediaId).ifPresent(Media::markAsFailed));
    }

    private void runConversionPipeline(Long mediaId) throws Exception {
        String originalKey = transactionTemplate.execute(status ->
                mediaRepository.findById(mediaId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Media disappeared mid-conversion: id=" + mediaId))
                        .getS3Key());

        String inputExt = extractExtension(originalKey);
        Path inputPath = null;
        Path webmPath = null;
        Path thumbPath = null;

        try {
            Files.createDirectories(Paths.get(TEMP_DIR));
            inputPath = Paths.get(TEMP_DIR, mediaId + "_input" + (inputExt.isEmpty() ? "" : "." + inputExt));
            webmPath = Paths.get(TEMP_DIR, mediaId + "_output.webm");
            thumbPath = Paths.get(TEMP_DIR, mediaId + "_thumb.webp");

            log.info("Downloading source video: mediaId={}, key={}", mediaId, originalKey);
            storageService.downloadToFile(originalKey, inputPath);

            runFfmpeg(List.of(
                    "ffmpeg", "-y",
                    "-i", inputPath.toString(),
                    "-c:v", "libvpx-vp9",
                    "-crf", "32",
                    "-b:v", "0",
                    "-an",
                    webmPath.toString()
            ), mediaId, "webm");

            runFfmpeg(List.of(
                    "ffmpeg", "-y",
                    "-i", inputPath.toString(),
                    "-ss", "00:00:01",
                    "-vframes", "1",
                    "-c:v", "libwebp",
                    thumbPath.toString()
            ), mediaId, "thumbnail");

            String webmKey = CONVERTED_PREFIX + mediaId + ".webm";
            String webmUrl = "%s/%s/%s".formatted(
                    storageProperties.getEndpoint(), storageProperties.getBucket(), webmKey);
            long webmSize = Files.size(webmPath);
            try (InputStream in = Files.newInputStream(webmPath)) {
                storageService.uploadFile(webmKey, in, webmSize, "video/webm");
            }

            String thumbKey = THUMBNAIL_PREFIX + mediaId + ".webp";
            long thumbSize = Files.size(thumbPath);
            try (InputStream in = Files.newInputStream(thumbPath)) {
                storageService.uploadFile(thumbKey, in, thumbSize, "image/webp");
            }

            transactionTemplate.executeWithoutResult(status -> {
                Media media = mediaRepository.findById(mediaId).orElseThrow(() ->
                        new IllegalStateException("Media disappeared post-conversion: id=" + mediaId));
                media.updateAfterConversion(webmKey, webmUrl, "video/webm", webmSize);
                media.updateThumbnailUrl(thumbKey);
                media.markAsReady();
            });

            try {
                storageService.deleteFile(originalKey);
                log.info("Deleted source video: mediaId={}, key={}", mediaId, originalKey);
            } catch (Exception e) {
                log.warn("Failed to delete source video (conversion still succeeded): mediaId={}, key={}",
                        mediaId, originalKey, e);
            }
        } finally {
            deleteQuietly(inputPath);
            deleteQuietly(webmPath);
            deleteQuietly(thumbPath);
        }
    }

    private void runFfmpeg(List<String> command, Long mediaId, String stage) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = pb.start();

        boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "FFmpeg %s timed out after %d minutes for mediaId=%d"
                            .formatted(stage, FFMPEG_TIMEOUT_MINUTES, mediaId));
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException(
                    "FFmpeg %s exited with code %d for mediaId=%d: %s"
                            .formatted(stage, exitCode, mediaId, output));
        }
    }

    private String extractExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }
}
