package com.example.godo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CompleteUploadRequest(
        @NotNull @Min(-90) @Max(90) Double latitude,
        @NotNull @Min(-180) @Max(180) Double longitude,
        String locationName,
        LocalDateTime capturedAt
) {
}
