package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.PasswordResetRequest;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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

        // 1. JWT에서 사용자 ID 추출
        UUID userId = UUID.fromString(jwt.getSubject());

        // 2. JWT 로그인 버전 추출 및 검증
        Object loginVersionClaim =
                jwt.getClaim("loginVersion");

        if (!(loginVersionClaim instanceof Number tokenLoginVersion)
                || tokenLoginVersion.doubleValue()
                != (double) tokenLoginVersion.longValue()) {

            throw new BadCredentialsException(
                    "사용할 수 없는 인증 토큰입니다."
            );
        }

        // 3. 사용자 ID 및 로그인 버전을 전달하여 비밀번호 변경
        passwordResetService.resetPassword(
                userId,
                request.newPassword(),
                tokenLoginVersion.longValue()
        );

        return ResponseEntity.noContent().build();
    }
}