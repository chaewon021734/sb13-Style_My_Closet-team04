package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenCookieProviderTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );

    private static final String RAW_TOKEN =
            "a".repeat(43);

    private static final String COOKIE_NAME =
            "refresh_token";

    private static final String COOKIE_PATH =
            "/api/auth";

    private RefreshTokenCookieProvider cookieProvider;

    @BeforeEach
    void setUp() {

        cookieProvider = new RefreshTokenCookieProvider();

        // 실제 HTTPS 배포 환경 기준 설정
        ReflectionTestUtils.setField(
                cookieProvider,
                "secure",
                true
        );
    }

    // 1. Refresh Token 쿠키 정상 생성
    @Test
    void shouldCreateRefreshTokenCookie() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertEquals(COOKIE_NAME, cookie.getName());
        assertEquals(RAW_TOKEN, cookie.getValue());
    }

    // 2. HttpOnly 설정 검증
    @Test
    void shouldCreateHttpOnlyCookie() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertTrue(cookie.isHttpOnly());
    }

    // 3. HTTPS 환경에서 Secure 설정 활성화
    @Test
    void shouldCreateSecureCookie() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertTrue(cookie.isSecure());
    }

    // 4. 로컬 HTTP 환경에서 Secure 설정 비활성화
    @Test
    void shouldCreateNonSecureCookieForLocalEnvironment() {

        ReflectionTestUtils.setField(
                cookieProvider,
                "secure",
                false
        );

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertFalse(cookie.isSecure());

        // 로컬 환경에서도 HttpOnly 설정은 유지
        assertTrue(cookie.isHttpOnly());
    }

    // 5. SameSite 설정 검증
    @Test
    void shouldSetSameSiteLax() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertEquals("Lax", cookie.getSameSite());
    }

    // 6. 쿠키 접근 경로 제한 검증
    @Test
    void shouldRestrictCookiePath() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        assertEquals(COOKIE_PATH, cookie.getPath());
    }

    // 7. 남은 토큰 유효기간에 맞춰 Max-Age 설정
    @Test
    void shouldSetMaxAgeBasedOnRemainingLifetime() {

        Instant expiresAt =
                Instant.now().plusSeconds(3600);

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(expiresAt)
        );

        long maxAge = cookie.getMaxAge().getSeconds();

        assertTrue(maxAge > 0);
        assertTrue(maxAge <= 3600);
    }

    // 8. 갱신된 토큰의 기존 만료 시간 유지 검증
    @Test
    void shouldNotExtendCookieLifetimeBeyondTokenExpiration() {

        Instant expiresAt =
                Instant.now().plusSeconds(120);

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(expiresAt)
        );

        long maxAge = cookie.getMaxAge().getSeconds();

        assertTrue(maxAge > 0);
        assertTrue(maxAge <= 120);
    }

    // 9. 이미 만료된 토큰의 쿠키 Max-Age는 0
    @Test
    void shouldSetZeroMaxAgeForExpiredToken() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().minusSeconds(60)
                )
        );

        assertEquals(
                0,
                cookie.getMaxAge().getSeconds()
        );
    }

    // 10. Refresh Token 쿠키 삭제
    @Test
    void shouldClearRefreshTokenCookie() {

        ResponseCookie cookie = cookieProvider.clear();

        assertEquals(COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());

        assertEquals(
                0,
                cookie.getMaxAge().getSeconds()
        );
    }

    // 11. 쿠키 삭제 시 기존 보안 속성 유지
    @Test
    void shouldPreserveSecurityAttributesWhenClearingCookie() {

        ResponseCookie cookie = cookieProvider.clear();

        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());

        assertEquals(
                "Lax",
                cookie.getSameSite()
        );

        assertEquals(
                COOKIE_PATH,
                cookie.getPath()
        );
    }

    // 12. 로컬 환경에서 쿠키 삭제 시 Secure 비활성화 유지
    @Test
    void shouldClearNonSecureCookieForLocalEnvironment() {

        ReflectionTestUtils.setField(
                cookieProvider,
                "secure",
                false
        );

        ResponseCookie cookie = cookieProvider.clear();

        assertFalse(cookie.isSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals(COOKIE_PATH, cookie.getPath());

        assertEquals(
                0,
                cookie.getMaxAge().getSeconds()
        );
    }

    // 13. 실제 Set-Cookie 헤더 문자열 형식 검증
    @Test
    void shouldGenerateExpectedSetCookieHeader() {

        ResponseCookie cookie = cookieProvider.create(
                createIssuedToken(
                        Instant.now().plusSeconds(3600)
                )
        );

        String header = cookie.toString();

        assertTrue(
                header.startsWith(
                        COOKIE_NAME + "=" + RAW_TOKEN
                )
        );

        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Lax"));
        assertTrue(header.contains("Path=/api/auth"));
        assertTrue(header.contains("Max-Age="));
    }

    // 14. 쿠키 삭제 헤더 형식 검증
    @Test
    void shouldGenerateExpectedClearCookieHeader() {

        String header = cookieProvider.clear()
                .toString();

        assertTrue(
                header.startsWith(COOKIE_NAME + "=")
        );

        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("Path=/api/auth"));
        assertTrue(header.contains("HttpOnly"));
    }

    // 공통 테스트 객체 생성
    private RefreshTokenService.IssuedRefreshToken
    createIssuedToken(Instant expiresAt) {

        return new RefreshTokenService.IssuedRefreshToken(
                USER_ID,
                RAW_TOKEN,
                expiresAt
        );
    }
}