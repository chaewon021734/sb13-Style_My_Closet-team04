package com.project.sb13ootbsteam04.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refresh_token";

    private static final String COOKIE_PATH = "/api/auth";

    @Value("${jwt.refresh-token-cookie-secure:true}")
    private boolean secure;

    // Refresh Token 쿠키 생성
    public ResponseCookie create(
            RefreshTokenService.IssuedRefreshToken token
    ) {
        Duration remaining = Duration.between(
                Instant.now(),
                token.expiresAt()
        );

        long maxAge = Math.max(0, remaining.getSeconds());

        return ResponseCookie
                .from(COOKIE_NAME, token.rawToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    // Refresh Token 쿠키 삭제
    public ResponseCookie clear() {
        return ResponseCookie
                .from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}