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

    private final JwtEncoder jwtEncoder;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    // 일반 비밀번호 로그인용 JWT 발급
    public String generateAccessToken(User user) {
        return generateToken(user, "NORMAL");
    }

    // 임시 비밀번호 로그인용 JWT 발급
    public String generateTemporaryAccessToken(User user) {
        return generateToken(user, "TEMPORARY");
    }

    // JWT 공통 생성 로직
    private String generateToken(User user, String authType) {

        Instant now = Instant.now();

        // 일반 토큰은 설정된 만료 시간 사용
        // 임시 비밀번호 토큰은 최대 3분 동안만 사용 가능
        Instant expiresAt;

        if ("TEMPORARY".equals(authType)) {
            expiresAt = now.plusSeconds(180);
        } else {
            expiresAt = now.plusMillis(accessTokenExpiration);
        }

        // JWT에 포함할 사용자 정보
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())

                // 재로그인 시 기존 JWT 무효화
                .claim("loginVersion", user.getLoginVersion())

                // 일반 로그인 또는 임시 비밀번호 로그인 구분
                .claim("authType", authType)

                .build();

        // HS256 서명 알고리즘
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .build();

        // JWT 생성
        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }
}
