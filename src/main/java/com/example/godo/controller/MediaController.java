package com.example.godo.controller;

import com.example.godo.dto.CompleteUploadRequest;
import com.example.godo.dto.LocationDto;
import com.example.godo.dto.MediaResponse;
import com.example.godo.dto.ReorderItem;
import com.example.godo.dto.UploadUrlRequest;
import com.example.godo.dto.UploadUrlResponse;
import com.example.godo.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "미디어 업로드 및 관리 API")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload-url")
    @Operation(summary = "Presigned 업로드 URL 발급 (관리자 전용)")
    public ResponseEntity<UploadUrlResponse> createUploadUrl(
            @Valid @RequestBody UploadUrlRequest request) {
        return ResponseEntity.ok(mediaService.createUploadUrl(request));
    }

    @PostMapping("/{mediaId}/complete")
    @Operation(summary = "업로드 완료 처리 (관리자 전용)")
    public ResponseEntity<MediaResponse> completeUpload(
            @PathVariable Long mediaId,
            @Valid @RequestBody CompleteUploadRequest request) {
        return ResponseEntity.ok(mediaService.completeUpload(mediaId, request));
    }

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "미디어 삭제 (관리자 전용)")
    public ResponseEntity<Void> deleteMedia(@PathVariable Long mediaId) {
        mediaService.deleteMedia(mediaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "전체 미디어 목록 조회 (공개)")
    public ResponseEntity<List<MediaResponse>> getAllMedia(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(mediaService.getAllMedia(page, size));
    }

    @PutMapping("/reorder")
    @Operation(summary = "미디어 순서 재정렬 (관리자 전용)")
    public ResponseEntity<Void> reorderMedia(@RequestBody List<ReorderItem> items) {
        mediaService.reorderMedia(items);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/locations")
    @Operation(summary = "지도용 모든 위치 조회 (공개)")
    public ResponseEntity<List<LocationDto>> getAllLocations() {
        return ResponseEntity.ok(mediaService.getAllLocations());
    }

    @GetMapping("/nearby")
    @Operation(summary = "위치 기반 미디어 조회 (공개)")
    public ResponseEntity<Page<MediaResponse>> getNearbyMedia(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "0.001") Double radius,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(mediaService.getNearbyMedia(lat, lng, radius, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "미디어 단건 조회 (공개)")
    public ResponseEntity<MediaResponse> getMedia(@PathVariable Long id) {
        return ResponseEntity.ok(mediaService.getMedia(id));
    }
}
