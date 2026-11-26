package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.PasswordResetRequest;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

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

@Tag(
        name = "Auth",
        description = "사용자 로그인 및 JWT 인증 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ========================================
    // 비밀번호 재설정 API
    // ========================================

    @Operation(
            summary = "비밀번호 재설정",
            description = """
                    JWT 인증을 완료한 사용자의 비밀번호를 변경합니다.

                    요청 본문의 새 비밀번호로 변경하며,
                    성공 시 응답 본문 없이 204 상태 코드를 반환합니다.

                    Authorization 헤더에 Bearer Token을 전달해야 합니다.
                    """
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "비밀번호 변경 성공",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "새 비밀번호 요청 데이터 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "JWT 누락, 만료 또는 유효하지 않은 토큰"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음"
            )
    })
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
