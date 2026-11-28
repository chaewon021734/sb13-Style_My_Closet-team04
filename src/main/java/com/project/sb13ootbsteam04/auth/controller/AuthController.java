package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenCookieProvider;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final RefreshTokenService refreshTokenService;

    // ========================================
    // 1. 기존 JSON 기반 로그인
    // ========================================

    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {

        LoginResult result = authService.login(request);

        return createLoginResponse(result);
    }

    // ========================================
    // 2. 프론트엔드 Multipart 기반 로그인
    // ========================================

    @PostMapping(
            value = "/sign-in",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<LoginResponse> signIn(
            @RequestParam("username")
            @NotBlank(message = "이메일을 입력해주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.")
            String username,

            @RequestParam("password")
            @NotBlank(message = "비밀번호를 입력해주세요.")
            String password
    ) {

        LoginRequest request = new LoginRequest(
                username,
                password
        );

        LoginResult result = authService.login(request);

        return createLoginResponse(result);
    }

    // ========================================
    // 3. Refresh Token을 이용한 Access Token 재발급
    // ========================================

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(
                    name = RefreshTokenCookieProvider.COOKIE_NAME,
                    required = false
            )
            String refreshToken
    ) {

        LoginResult result = refreshTokenService.rotate(
                refreshToken
        );

        return createLoginResponse(result);
    }

    // ========================================
    // 4. 로그아웃
    // ========================================

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @CookieValue(
                    name = RefreshTokenCookieProvider.COOKIE_NAME,
                    required = false
            )
            String refreshToken
    ) {

        // 유효한 Refresh Token 폐기 및 loginVersion 증가
        refreshTokenService.logout(refreshToken);

        // 브라우저에 저장된 Refresh Token 쿠키 삭제
        ResponseCookie cookie =
                refreshTokenCookieProvider.clear();

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .build();
    }

    // ========================================
    // 5. 공통 로그인 및 토큰 갱신 응답
    // ========================================

    // Access Token은 응답 Body,
    // Refresh Token은 HttpOnly 쿠키로 전달
    private ResponseEntity<LoginResponse> createLoginResponse(
            LoginResult result
    ) {

        ResponseCookie cookie;

        if (result.hasRefreshToken()) {

            cookie = refreshTokenCookieProvider.create(
                    result.refreshToken()
            );

        } else {

            cookie = refreshTokenCookieProvider.clear();
        }

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                )
                .header(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                )
                .body(result.response());
    }
}