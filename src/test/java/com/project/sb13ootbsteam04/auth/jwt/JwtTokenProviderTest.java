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
import java.util.Base64;

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

    @Test
    void shouldGenerateAndVerifyAccessToken() {

        // given
        User user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                java.util.UUID.randomUUID()
        );

        // when
        String token = jwtTokenProvider.generateAccessToken(user);
        Jwt decodedJwt = jwtDecoder.decode(token);

        // then
        assertNotNull(token);
        assertNotNull(decodedJwt.getIssuedAt());
        assertNotNull(decodedJwt.getExpiresAt());
        assertEquals("USER", decodedJwt.getClaimAsString("role"));
    }
}
