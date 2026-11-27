package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.global.config.JwtConfig;
import com.project.sb13ootbsteam04.user.entity.User;
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
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {

        // 테스트 전용 32바이트 키 (실제 서비스 키와 무관)
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) (i + 1);
        }

        String encodedKey = Base64.getEncoder()
                .encodeToString(keyBytes);

        JwtConfig jwtConfig = new JwtConfig();

        SecretKey secretKey = jwtConfig.jwtSecretKey(encodedKey);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(secretKey);

        // HS256 알고리즘으로 서명된 JWT 검증
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
    }

    // ========================================
    // 1. JWT 발급 및 기본 클레임 검증
    // ========================================

    @Test
    void shouldGenerateAndVerifyAccessToken() {

        // given
        User user = createUser();

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertNotNull(token);
        assertEquals(user.getId().toString(), decodedJwt.getSubject());
        assertNotNull(decodedJwt.getIssuedAt());
        assertNotNull(decodedJwt.getExpiresAt());

        // 사용자 권한 확인
        assertEquals("USER", decodedJwt.getClaimAsString("role"));

        // 초기 로그인 버전 확인
        assertEquals(
                0L,
                ((Number) decodedJwt.getClaim("loginVersion")).longValue()
        );

        // JWT 발급 후에도 User의 로그인 버전은 변경되지 않음
        assertEquals(0L, user.getLoginVersion());
    }

    // ========================================
    // 2. 로그인 버전 증가 후 JWT 발급
    // ========================================

    @Test
    void shouldIncludeIncreasedLoginVersionInJwt() {

        // given
        User user = createUser();

        user.increaseLoginVersion();

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertEquals(1L, user.getLoginVersion());

        assertEquals(
                1L,
                ((Number) decodedJwt.getClaim("loginVersion")).longValue()
        );
    }

    // ========================================
    // 3. 재로그인 시 새로운 로그인 버전 반영
    // ========================================

    @Test
    void shouldIssueJwtWithUpdatedVersionAfterRelogin() {

        // given
        User user = createUser();

        // 첫 번째 로그인
        user.increaseLoginVersion();

        String firstToken = jwtTokenProvider.generateAccessToken(user);

        Jwt firstJwt = jwtDecoder.decode(firstToken);

        // 두 번째 로그인
        user.increaseLoginVersion();

        String secondToken = jwtTokenProvider.generateAccessToken(user);

        Jwt secondJwt = jwtDecoder.decode(secondToken);

        // then
        assertEquals(
                1L,
                ((Number) firstJwt.getClaim("loginVersion")).longValue()
        );

        assertEquals(
                2L,
                ((Number) secondJwt.getClaim("loginVersion")).longValue()
        );

        assertEquals(2L, user.getLoginVersion());

        assertNotEquals(firstToken, secondToken);
    }

    // ========================================
    // 4. JWT 만료 시간 검증
    // ========================================

    @Test
    void shouldUseConfiguredAccessTokenExpiration() {

        // given
        User user = createUser();

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertNotNull(decodedJwt.getIssuedAt());
        assertNotNull(decodedJwt.getExpiresAt());

        Duration duration = Duration.between(
                decodedJwt.getIssuedAt(),
                decodedJwt.getExpiresAt()
        );

        // 설정된 만료 시간: 1시간
        assertEquals(Duration.ofHours(1), duration);
    }

    // ========================================
    // 테스트용 사용자 생성
    // ========================================

    private User createUser() {

        User user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                UUID.randomUUID()
        );

        return user;
    }
}
