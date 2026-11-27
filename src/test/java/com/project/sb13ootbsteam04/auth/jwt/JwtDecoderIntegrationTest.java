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

    // 테스트용 실제 JWT 발급
    private String createToken() {

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

    // 1. 정상 계정이면 JWT 검증 성공
    @Test
    void shouldAcceptTokenForActiveUser() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        String token = createToken();

        Jwt decodedJwt = jwtDecoder.decode(token);

        assertEquals(
                userId.toString(),
                decodedJwt.getSubject()
        );

        assertEquals(
                "USER",
                decodedJwt.getClaimAsString("role")
        );

        verify(userRepository).findById(userId);
    }

    // 2. 잠긴 계정이면 기존 JWT 검증 실패
    @Test
    void shouldRejectTokenForLockedUser() {

        ReflectionTestUtils.setField(
                user,
                "locked",
                true
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        String token = createToken();

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );

        verify(userRepository).findById(userId);
    }

    // 3. 삭제된 계정이면 기존 JWT 검증 실패
    @Test
    void shouldRejectTokenForDeletedUser() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        String token = createToken();

        assertThrows(
                JwtException.class,
                () -> jwtDecoder.decode(token)
        );

        verify(userRepository).findById(userId);
    }
}
