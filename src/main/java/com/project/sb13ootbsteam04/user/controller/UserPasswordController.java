package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.user.dto.request.UserPasswordUpdateRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserPasswordController {

    private final PasswordResetService passwordResetService;

    // 프론트엔드 호환 비밀번호 재설정 API
    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserPasswordUpdateRequest request
    ) {

        // 1. 임시 JWT 인증 여부 확인
        if (jwt == null
                || !"TEMPORARY".equals(
                        jwt.getClaimAsString("authType")
                )) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "비밀번호 재설정 권한이 없습니다."
            );
        }

        // 2. JWT에서 인증된 사용자 ID 추출
        UUID authenticatedUserId =
                UUID.fromString(jwt.getSubject());

        // 3. 다른 사용자의 비밀번호 변경 차단
        if (!authenticatedUserId.equals(userId)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "다른 사용자의 비밀번호를 변경할 수 없습니다."
            );
        }

        // 4. JWT 로그인 버전 추출 및 검증
        Object loginVersionClaim =
                jwt.getClaim("loginVersion");

        if (!(loginVersionClaim instanceof Number tokenLoginVersion)
                || tokenLoginVersion.doubleValue()
                != (double) tokenLoginVersion.longValue()) {

            throw new BadCredentialsException(
                    "사용할 수 없는 인증 토큰입니다."
            );
        }

        // 5. 기존 비밀번호 재설정 로직 재사용
        passwordResetService.resetPassword(
                authenticatedUserId,
                request.password(),
                tokenLoginVersion.longValue()
        );

        // 6. 정상 변경 시 204 No Content 반환
        return ResponseEntity.noContent().build();
    }
}