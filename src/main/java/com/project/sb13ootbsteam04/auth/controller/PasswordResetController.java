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

    // 임시 JWT 인증 후 비밀번호 재설정
    @PatchMapping("/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordResetRequest request
    ) {

        // SecurityConfig의 PASSWORD_RESET 권한 검증을 통과한
        // JWT에서 사용자 ID 추출
        UUID userId = UUID.fromString(jwt.getSubject());

        // 새로운 비밀번호로 변경
        passwordResetService.resetPassword(
                userId,
                request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }
}
