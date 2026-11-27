package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.user.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String NORMAL = "NORMAL";
    private static final String TEMPORARY = "TEMPORARY";

    private final JwtEncoder jwtEncoder;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    // ========================================
    // 1. 일반 로그인 JWT 발급
    // ========================================

    public String generateAccessToken(User user) {

        Instant now = Instant.now();

        Instant expiresAt =
                now.plusMillis(accessTokenExpiration);

        return generateToken(
                user,
                NORMAL,
                now,
                expiresAt
        );
    }

    // ========================================
    // 2. 임시 비밀번호 로그인 JWT 발급
    // ========================================

    public String generateTemporaryAccessToken(User user) {

        Instant now = Instant.now();

        // 임시 비밀번호가 유효할 때만 JWT 발급
        if (!user.isTemporaryPasswordActive(now)) {
            throw new IllegalStateException(
                    "임시 비밀번호가 만료되었거나 유효하지 않습니다."
            );
        }

        // 임시 비밀번호 자체의 만료 시간 사용
        // 발급 시점부터 3분을 새로 부여하지 않음
        Instant expiresAt =
                user.getTemporaryPasswordExpiresAt();

        return generateToken(
                user,
                TEMPORARY,
                now,
                expiresAt
        );
    }

    // ========================================
    // 3. 공통 JWT 생성
    // ========================================

    private String generateToken(
            User user,
            String authType,
            Instant now,
            Instant expiresAt
    ) {

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)

                // 사용자 권한
                .claim("role", user.getRole().name())

                // 기존 JWT 무효화용 로그인 버전
                .claim(
                        "loginVersion",
                        user.getLoginVersion()
                )

                // 일반 인증 / 임시 인증 구분
                .claim("authType", authType)

                .build();

        JwsHeader header =
                JwsHeader.with(MacAlgorithm.HS256)
                        .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
