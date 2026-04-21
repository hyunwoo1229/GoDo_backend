package com.example.godo.dto;

import com.example.godo.entity.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record UploadUrlRequest(
        @NotBlank String fileName,
        @NotBlank @Pattern(regexp = "^(image|video)/.*") String contentType,
        @NotNull MediaType mediaType,
        @NotNull @Positive Long fileSize
) {
}
