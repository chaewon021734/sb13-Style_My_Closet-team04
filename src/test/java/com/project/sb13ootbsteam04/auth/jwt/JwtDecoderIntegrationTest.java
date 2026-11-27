package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.global.config.JwtConfig;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtDecoderIntegrationTest {

    private UserRepository userRepository;
    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {

        // 1. DB 없이 사용할 Mock Repository
        userRepository = mock(UserRepository.class);

        // 2. 테스트용 JWT 비밀 키
        byte[] keyBytes = new byte[32];

        for (int i = 0; i < keyBytes.length; i++) {
            keyBytes[i] = (byte) (i + 1);
        }

        String secret = Base64.getEncoder()
                .encodeToString(keyBytes);

        // 3. 실제 JWT 설정 사용
        JwtConfig jwtConfig = new JwtConfig();

        SecretKey secretKey =
                jwtConfig.jwtSecretKey(secret);

        jwtEncoder = jwtConfig.jwtEncoder(secretKey);

        // 4. 사용자 상태 검증기 연결
        JwtAccountStatusValidator validator =
                new JwtAccountStatusValidator(userRepository);

        jwtDecoder = jwtConfig.jwtDecoder(
                secretKey,
                validator
        );

        // 5. 테스트 사용자 생성
        userId = UUID.randomUUID();

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                userId
        );
    }

    // ========================================
    // 테스트용 실제 JWT 발급
    // ========================================

    private String createToken(long loginVersion) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .claim("loginVersion", loginVersion)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    // 로그인 버전 클레임이 없는 기존 JWT 생성
    private String createTokenWithoutLoginVersion() {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    // ========================================
    // 1. 정상 계정 JWT 검증 성공
    // ========================================

    @Test
    void shouldAcceptTokenForActiveUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        String token = createToken(0L);

        // when
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertEquals(
                userId.toString(),
                decodedJwt.getSubject()
        );

        assertEquals(
                "USER",
                decodedJwt.getClaimAsString("role")
        );

        assertEquals(
                0L,
                ((Number) decodedJwt.getClaim("loginVersion")).longValue()
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 2. 잠긴 계정 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectTokenForLockedUser() {

        // given
        ReflectionTestUtils.setField(
                user,
                "locked",
                true
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        String token = createToken(0L);

        // when & then
        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 3. 삭제된 계정 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectTokenForDeletedUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        String token = createToken(0L);

        // when & then
        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );

        verify(userRepository).findById(userId);
    }

    // ========================================
    // 4. 재로그인 후 기존 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectOldTokenAfterRelogin() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // 첫 번째 로그인
        user.increaseLoginVersion();

        String firstToken = createToken(user.getLoginVersion());

        // 첫 번째 JWT는 정상적으로 인증됨
        Jwt firstJwt = jwtDecoder.decode(firstToken);

        assertEquals(
                1L,
                ((Number) firstJwt.getClaim("loginVersion")).longValue()
        );

        // 두 번째 로그인
        user.increaseLoginVersion();

        String secondToken = createToken(user.getLoginVersion());

        // when & then

        // 기존 JWT는 로그인 버전이 달라 인증 실패
        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(firstToken)
        );

        // 새로운 JWT는 정상적으로 인증됨
        Jwt secondJwt = jwtDecoder.decode(secondToken);

        assertEquals(
                2L,
                ((Number) secondJwt.getClaim("loginVersion")).longValue()
        );

        verify(userRepository, times(3)).findById(userId);
    }

    // ========================================
    // 5. loginVersion 없는 기존 JWT 검증 실패
    // ========================================

    @Test
    void shouldRejectTokenWithoutLoginVersion() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        String token = createTokenWithoutLoginVersion();

        // when & then
        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );

        verify(userRepository).findById(userId);
    }
}
