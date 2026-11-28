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

    private static final String NORMAL = "NORMAL";
    private static final String TEMPORARY = "TEMPORARY";

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {

        // 1. JWT subject에서 사용자 UUID 추출
        UUID userId;

        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            return invalidToken();
        }

        // 2. DB 사용자 조회
        Optional<User> userOptional =
                userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            return invalidToken();
        }

        User user = userOptional.get();

        // 3. 계정 잠금 여부 검증
        if (user.isLocked()) {
            return invalidToken();
        }

        // 4. JWT의 역할과 DB 역할 비교
        String tokenRole = jwt.getClaimAsString("role");

        if (tokenRole == null
                || user.getRole() == null
                || !user.getRole().name().equals(tokenRole)) {
            return invalidToken();
        }

        // 5. 로그인 버전 검증
        Object loginVersionClaim =
                jwt.getClaim("loginVersion");

        if (!(loginVersionClaim instanceof Number tokenLoginVersion)) {
            return invalidToken();
        }

        if (tokenLoginVersion.longValue() != user.getLoginVersion()
                || tokenLoginVersion.doubleValue()
                != (double) tokenLoginVersion.longValue()) {
            return invalidToken();
        }

        // 6. 인증 유형 검증
        String authType = jwt.getClaimAsString("authType");

        // PR A에서 발급된 기존 JWT와의 호환성 유지
        if (authType == null) {
            authType = NORMAL;
        }

        if (!NORMAL.equals(authType)
                && !TEMPORARY.equals(authType)) {
            return invalidToken();
        }

        // 7. 임시 JWT는 임시 비밀번호가 유효해야 함
        if (TEMPORARY.equals(authType)
                && !user.isTemporaryPasswordActive(Instant.now())) {
            return invalidToken();
        }

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
