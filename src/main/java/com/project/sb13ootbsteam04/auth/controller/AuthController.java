package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ========================================
    // 로그인 API
    // ========================================

    @Operation(
            summary = "사용자 로그인",
            description = """
                    이메일과 비밀번호로 로그인합니다.

                    로그인 성공 시 JWT Access Token을 반환합니다.

                    동일 계정에서 로그인에 5회 실패하면
                    10분 동안 로그인이 제한됩니다.

                    IP별 요청 제한 정책도 적용됩니다.
                    """
    )
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            schema = @Schema(
                                    implementation = LoginResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 데이터 검증 실패"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 오류"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "로그인 실패 횟수 초과 또는 IP 요청 제한"
            )
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(response);
    }
}
