package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenGeneratorTest {

    private RefreshTokenGenerator refreshTokenGenerator;

    @BeforeEach
    void setUp() {
        refreshTokenGenerator = new RefreshTokenGenerator();
    }

    // 1. Refresh Token 정상 생성
    @Test
    void shouldGenerateRefreshToken() {

        String token = refreshTokenGenerator.generate();

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(43, token.length());
    }

    // 2. URL-safe Base64 형식 검증
    @Test
    void shouldGenerateUrlSafeToken() {

        String token = refreshTokenGenerator.generate();

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));

        assertFalse(token.contains("+"));
        assertFalse(token.contains("/"));
        assertFalse(token.contains("="));
    }

    // 3. 생성된 토큰의 원본 데이터 길이 검증
    @Test
    void shouldGenerate256BitToken() {

        String token = refreshTokenGenerator.generate();

        byte[] decodedToken = Base64.getUrlDecoder()
                .decode(token);

        assertEquals(32, decodedToken.length);
    }

    // 4. 반복 생성 시 토큰 중복 여부 검증
    @Test
    void shouldGenerateUniqueTokens() {

        Set<String> tokens = new HashSet<>();

        for (int i = 0; i < 1000; i++) {

            String token = refreshTokenGenerator.generate();

            assertTrue(
                    tokens.add(token),
                    "중복된 Refresh Token이 생성되었습니다."
            );
        }

        assertEquals(1000, tokens.size());
    }

    // 5. SHA-256 해시 형식 검증
    @Test
    void shouldGenerateSha256Hash() {

        String rawToken = refreshTokenGenerator.generate();

        String hashedToken =
                refreshTokenGenerator.hash(rawToken);

        assertNotNull(hashedToken);
        assertEquals(64, hashedToken.length());

        assertTrue(
                hashedToken.matches("[0-9a-f]{64}")
        );

        assertNotEquals(rawToken, hashedToken);
    }

    // 6. SHA-256 알고리즘 결과 검증
    @Test
    void shouldGenerateExpectedSha256Hash() {

        String expectedHash =
                "ba7816bf8f01cfea414140de5dae2223"
                + "b00361a396177a9cb410ff61f20015ad";

        String actualHash =
                refreshTokenGenerator.hash("abc");

        assertEquals(expectedHash, actualHash);
    }

    // 7. 동일한 토큰은 동일한 해시 생성
    @Test
    void shouldGenerateSameHashForSameToken() {

        String rawToken = refreshTokenGenerator.generate();

        String firstHash =
                refreshTokenGenerator.hash(rawToken);

        String secondHash =
                refreshTokenGenerator.hash(rawToken);

        assertEquals(firstHash, secondHash);
    }

    // 8. 서로 다른 토큰은 서로 다른 해시 생성
    @Test
    void shouldGenerateDifferentHashesForDifferentTokens() {

        String firstHash =
                refreshTokenGenerator.hash("a".repeat(43));

        String secondHash =
                refreshTokenGenerator.hash("b".repeat(43));

        assertNotEquals(firstHash, secondHash);
    }

    // 9. null 토큰 해시 생성 거부
    @Test
    void shouldRejectNullToken() {

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenGenerator.hash(null)
        );

        assertEquals(
                "Refresh Token은 필수입니다.",
                exception.getMessage()
        );
    }

    // 10. 빈 문자열 토큰 해시 생성 거부
    @Test
    void shouldRejectEmptyToken() {

        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenGenerator.hash("")
        );
    }

    // 11. 공백 문자열 토큰 해시 생성 거부
    @Test
    void shouldRejectBlankToken() {

        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenGenerator.hash("   ")
        );
    }
}