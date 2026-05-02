package com.example.godo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    @PostMapping("/verify")
    @Operation(summary = "관리자 인증 정보 검증")
    public ResponseEntity<Void> verifyCredentials() {
        return ResponseEntity.ok().build();
    }
}
