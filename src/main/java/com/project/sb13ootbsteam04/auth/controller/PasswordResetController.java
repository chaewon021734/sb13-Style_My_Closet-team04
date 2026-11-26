package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.PasswordResetRequest;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PatchMapping("/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordResetRequest request
    ) {

        // 1. 검증된 JWT에서 사용자 UUID 추출
        UUID userId = UUID.fromString(jwt.getSubject());

        // 2. 새 비밀번호로 재설정
        passwordResetService.resetPassword(
                userId,
                request.newPassword()
        );

        // 3. 비밀번호 변경 성공 시 204 응답
        return ResponseEntity.noContent().build();
    }
}
