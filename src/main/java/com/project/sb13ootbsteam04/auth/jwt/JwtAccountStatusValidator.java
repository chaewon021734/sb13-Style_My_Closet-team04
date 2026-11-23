package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAccountStatusValidator
        implements OAuth2TokenValidator<Jwt> {

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {

        // 1. JWT의 sub에서 사용자 ID 가져오기
        String subject = jwt.getSubject();

        UUID userId;

        // 2. UUID 형식 검증
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException e) {
            return invalidToken();
        }

        // 3. 데이터베이스에서 사용자 조회
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            return invalidToken();
        }

        User user = userOptional.get();

        // 4. 잠긴 계정인지 검증
        if (user.isLocked()) {
            return invalidToken();
        }

        // 5. JWT 역할과 DB 역할 비교
        String tokenRole = jwt.getClaimAsString("role");

        if (tokenRole == null
                || user.getRole() == null
                || !user.getRole().name().equals(tokenRole)) {
            return invalidToken();
        }

        // 6. JWT 로그인 버전 검증
        Object loginVersionClaim = jwt.getClaim("loginVersion");

        if (!(loginVersionClaim instanceof Number tokenLoginVersion)) {
            return invalidToken();
        }

        if (tokenLoginVersion.longValue() != user.getLoginVersion()
                || tokenLoginVersion.doubleValue()
                != (double) tokenLoginVersion.longValue()) {
            return invalidToken();
        }

        // 7. JWT 인증 유형 확인
        String authType = jwt.getClaimAsString("authType");

        // 기존 JWT 호환을 위해 authType이 없는 토큰은
        // 일반 로그인으로 취급
        if (authType == null) {
            authType = "NORMAL";
        }

        // 허용된 인증 유형만 통과
        if (!"NORMAL".equals(authType)
                && !"TEMPORARY".equals(authType)) {
            return invalidToken();
        }

        // 8. 임시 비밀번호 JWT 추가 검증
        if ("TEMPORARY".equals(authType)) {

            // 임시 비밀번호가 이미 삭제되었거나 만료되면 거부
            if (!user.isTemporaryPasswordActive(Instant.now())) {
                return invalidToken();
            }
        }

        // 9. 모든 검증을 통과하면 성공
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalidToken() {

        OAuth2Error error = new OAuth2Error(
                "invalid_token",
                "사용할 수 없는 인증 토큰입니다.",
                null
        );

        return OAuth2TokenValidatorResult.failure(error);
    }
}
