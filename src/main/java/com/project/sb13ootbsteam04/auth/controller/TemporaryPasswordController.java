package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.TemporaryPasswordRequest;
import com.project.sb13ootbsteam04.auth.ratelimit.TemporaryPasswordRateLimiter;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TemporaryPasswordController {

    private final TemporaryPasswordService temporaryPasswordService;
    private final TemporaryPasswordRateLimiter rateLimiter;

    // 기존 API 및 프론트엔드 호환 API 지원
    @PostMapping({"/temporary-password", "/reset-password"})
    public ResponseEntity<Void> issueTemporaryPassword(
            @Valid @RequestBody TemporaryPasswordRequest request,
            HttpServletRequest httpRequest
    ) {

        // 1. 클라이언트 IP 확인
        String ipAddress = httpRequest.getRemoteAddr();

        // 2. 이메일 및 IP별 요청 횟수 제한
        // 제한 초과 시 TemporaryPasswordRateLimitException 발생
        rateLimiter.checkAndRecord(
                request.email(),
                ipAddress
        );

        // 3. 요청 제한 통과 시 기존 임시 비밀번호 발급
        temporaryPasswordService.issueTemporaryPassword(
                request.email()
        );

        // 4. 정상 요청은 202 Accepted 반환
        return ResponseEntity.accepted().build();
    }
}