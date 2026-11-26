package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.TemporaryPasswordRequest;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Auth",
        description = "사용자 로그인 및 JWT 인증 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TemporaryPasswordController {

    private final TemporaryPasswordService temporaryPasswordService;

    // ========================================
    // 임시 비밀번호 발급 API
    // ========================================

    @Operation(
            summary = "임시 비밀번호 발급",
            description = """
                    이메일을 입력받아 임시 비밀번호 발급을 요청합니다.

                    임시 비밀번호 발급 처리가 완료되면
                    202 Accepted 상태 코드를 반환합니다.

                    임시 비밀번호의 유효기간은 3분이며,
                    IP별 요청 횟수 제한이 적용됩니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "임시 비밀번호 발급 요청 처리 완료",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 요청 데이터 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "요청 횟수 제한 초과"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    @PostMapping("/temporary-password")
    public ResponseEntity<Void> issueTemporaryPassword(
            @Valid @RequestBody TemporaryPasswordRequest request
    ) {

        temporaryPasswordService.issueTemporaryPassword(
                request.email()
        );

        return ResponseEntity.accepted().build();
    }
}
