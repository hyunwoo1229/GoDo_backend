package com.example.godo.service;

import com.example.godo.config.StorageProperties;
import com.example.godo.entity.Media;
import com.example.godo.entity.MediaType;
import com.example.godo.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class FfmpegThumbnailService implements ThumbnailService {

    private static final String TEMP_DIR = "/tmp/godo-thumbnails";
    private static final int FFMPEG_TIMEOUT_MINUTES = 2;
    private static final String THUMBNAIL_PREFIX = "thumbnails/";

    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Async("thumbnailExecutor")
    @Transactional
    @Override
    public void generateThumbnailAsync(Long mediaId) {
        Optional<Media> maybeMedia = mediaRepository.findById(mediaId);
        if (maybeMedia.isEmpty()) {
            log.warn("Thumbnail generation skipped — media not found: id={}", mediaId);
            return;
        }
        Media media = maybeMedia.get();
        if (media.getMediaType() != MediaType.VIDEO) {
            log.debug("Thumbnail generation skipped — not a video: id={}, type={}",
                    mediaId, media.getMediaType());
            return;
        }

        Path videoPath = null;
        Path thumbnailPath = null;
        try {
            Files.createDirectories(Paths.get(TEMP_DIR));

            String extension = extractExtension(media.getOriginalFileName());
            videoPath = Paths.get(TEMP_DIR, mediaId + "_input" + (extension.isEmpty() ? "" : "." + extension));
            thumbnailPath = Paths.get(TEMP_DIR, mediaId + "_thumb.jpg");

            log.info("Downloading video for thumbnail generation: mediaId={}, s3Key={}",
                    mediaId, media.getS3Key());
            storageService.downloadToFile(media.getS3Key(), videoPath);

            runFfmpeg(videoPath, thumbnailPath, mediaId);

            String thumbnailKey = THUMBNAIL_PREFIX + mediaId + ".jpg";
            long thumbnailSize = Files.size(thumbnailPath);
            try (InputStream in = Files.newInputStream(thumbnailPath)) {
                storageService.uploadFile(thumbnailKey, in, thumbnailSize, "image/jpeg");
            }

            media.updateThumbnailUrl(thumbnailKey);
            log.info("Thumbnail generated successfully: mediaId={}, key={}", mediaId, thumbnailKey);
        } catch (Exception e) {
            log.error("Thumbnail generation failed for mediaId={}", mediaId, e);
        } finally {
            deleteQuietly(videoPath);
            deleteQuietly(thumbnailPath);
        }
    }

    private void runFfmpeg(Path videoPath, Path thumbnailPath, Long mediaId) throws Exception {
        List<String> command = List.of(
                "ffmpeg",
                "-i", videoPath.toString(),
                "-ss", "00:00:01",
                "-vframes", "1",
                "-y",
                thumbnailPath.toString()
        );
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        Process process = pb.start();

        boolean finished = process.waitFor(FFMPEG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "FFmpeg timed out after %d minutes for mediaId=%d"
                            .formatted(FFMPEG_TIMEOUT_MINUTES, mediaId));
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String output = new String(process.getInputStream().readAllBytes());
            throw new IllegalStateException(
                    "FFmpeg exited with code %d for mediaId=%d: %s"
                            .formatted(exitCode, mediaId, output));
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
