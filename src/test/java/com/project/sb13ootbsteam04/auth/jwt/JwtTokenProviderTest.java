package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.global.config.JwtConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.entity.UserRole;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtDecoder jwtDecoder;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {

        // 테스트 전용 32바이트 키
        byte[] keyBytes = new byte[32];

        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) (i + 1);
        }

        String encodedKey = Base64.getEncoder()
                .encodeToString(keyBytes);

        JwtConfig jwtConfig = new JwtConfig();

        SecretKey secretKey = jwtConfig.jwtSecretKey(encodedKey);

        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(secretKey);

        jwtDecoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        jwtTokenProvider = new JwtTokenProvider(jwtEncoder);

        // 토큰 만료 시간: 1시간
        ReflectionTestUtils.setField(
                jwtTokenProvider,
                "accessTokenExpiration",
                3600000L
        );

        // 테스트 사용자 생성
        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        userId = UUID.randomUUID();

        ReflectionTestUtils.setField(user, "id", userId);
    }

    // ========================================
    // 1. JWT 정상 발급 및 서명 검증
    // ========================================

    @Test
    void shouldGenerateAndVerifyAccessToken() {

        // when
        String token = jwtTokenProvider.generateAccessToken(user);

        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertNotNull(token);
        assertFalse(token.isBlank());

        assertNotNull(decodedJwt);

        assertNotNull(decodedJwt.getIssuedAt());
        assertNotNull(decodedJwt.getExpiresAt());

        assertEquals("USER", decodedJwt.getClaimAsString("role"));
    }

    // ========================================
    // 2. JWT에 사용자 UUID 포함
    // ========================================

    @Test
    void shouldIncludeUserIdInToken() {

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertEquals(
                userId.toString(),
                decodedJwt.getSubject()
        );
    }

    // ========================================
    // 3. JWT에 관리자 권한 포함
    // ========================================

    @Test
    void shouldIncludeAdminRoleInToken() {

        // given
        user.changeRole(UserRole.ADMIN);

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertEquals(
                "ADMIN",
                decodedJwt.getClaimAsString("role")
        );
    }

    // ========================================
    // 4. JWT에 로그인 버전 포함
    // ========================================

    @Test
    void shouldIncludeLoginVersionInToken() {

        // given
        user.increaseLoginVersion();
        user.increaseLoginVersion();

        // 현재 로그인 버전은 2
        assertEquals(2L, user.getLoginVersion());

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        Object loginVersionClaim =
                decodedJwt.getClaim("loginVersion");

        assertInstanceOf(Number.class, loginVersionClaim);

        assertEquals(
                2L,
                ((Number) loginVersionClaim).longValue()
        );
    }

    // ========================================
    // 5. JWT 만료 시간 1시간 확인
    // ========================================

    @Test
    void shouldSetAccessTokenExpirationToOneHour() {

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        Instant issuedAt = decodedJwt.getIssuedAt();
        Instant expiresAt = decodedJwt.getExpiresAt();

        assertNotNull(issuedAt);
        assertNotNull(expiresAt);

        // 발급 시간과 만료 시간 차이는 1시간
        Duration duration = Duration.between(
                issuedAt,
                expiresAt
        );

        assertEquals(
                3600L,
                duration.getSeconds()
        );
    }
}
