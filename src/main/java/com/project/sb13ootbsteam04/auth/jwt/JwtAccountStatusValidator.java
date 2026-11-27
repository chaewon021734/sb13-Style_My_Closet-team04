package com.project.sb13ootbsteam04.auth.jwt;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

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

        // 4. 사용자가 삭제되었으면 인증 실패
        if (userOptional.isEmpty()) {
            return invalidToken();
        }

        User user = userOptional.get();

        // 5. 잠긴 계정이면 인증 실패
        if (user.isLocked()) {
            return invalidToken();
        }

        // 6. JWT 역할과 DB 사용자 권한 일치 여부 검증
        String tokenRole = jwt.getClaimAsString("role");

        if (tokenRole == null
                || user.getRole() == null
                || !user.getRole().name().equals(tokenRole)) {
            return invalidToken();
        }

        // 7. JWT 로그인 버전 검증
        Object loginVersionClaim = jwt.getClaim("loginVersion");

        // 로그인 버전이 없거나 숫자가 아니면 인증 실패
        if (!(loginVersionClaim instanceof Number tokenLoginVersion)) {
            return invalidToken();
        }

        // JWT 로그인 버전과 DB 로그인 버전 비교
        if (tokenLoginVersion.longValue() != user.getLoginVersion()
                || tokenLoginVersion.doubleValue()
                != (double) tokenLoginVersion.longValue()) {
            return invalidToken();
        }

        // 8. 모든 검증을 통과하면 인증 성공
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
