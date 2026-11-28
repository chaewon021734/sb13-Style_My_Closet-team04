package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.entity.RefreshToken;
import com.project.sb13ootbsteam04.auth.entity.RefreshTokenRevocationReason;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_EXPIRATION =
            Duration.ofDays(7);

    // Rotation과 로그아웃 간 동시 요청 허용 시간
    private static final Duration LOGOUT_ROTATION_GRACE_PERIOD =
            Duration.ofSeconds(5);

    private static final String INVALID_TOKEN_MESSAGE =
            "유효하지 않은 Refresh Token입니다.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    // ========================================
    // 1. 신규 Refresh Token 발급
    // ========================================

    @Transactional
    public IssuedRefreshToken issue(User user) {

        User lockedUser = userRepository
                .findByIdForUpdate(user.getId())
                .orElseThrow(this::invalidToken);

        if (lockedUser.isLocked()) {
            throw invalidToken();
        }

        Instant now = Instant.now();

        return createToken(
                lockedUser,
                now,
                now.plus(REFRESH_TOKEN_EXPIRATION)
        );
    }

    // ========================================
    // 2. Refresh Token Rotation
    // ========================================

    @Transactional
    public LoginResult rotate(String rawToken) {

        validateTokenFormat(rawToken);

        String tokenHash =
                refreshTokenGenerator.hash(rawToken);

        User user = lockTokenOwner(tokenHash);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidToken);

        Instant now = Instant.now();

        if (!refreshToken.isValid(now)) {
            throw invalidToken();
        }

        // Rotation으로 인한 폐기 사유 기록
        refreshToken.revoke(
                now,
                RefreshTokenRevocationReason.ROTATED
        );

        // 최초 발급 시 설정한 만료 시간 유지
        IssuedRefreshToken newRefreshToken = createToken(
                user,
                now,
                refreshToken.getExpiresAt()
        );

        String accessToken = jwtTokenProvider
                .generateAccessToken(user);

        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                UserDto.from(user)
        );

        return new LoginResult(
                response,
                newRefreshToken
        );
    }

    // ========================================
    // 3. 일반 Refresh Token 폐기
    // ========================================

    @Transactional
    public void revoke(String rawToken) {

        validateTokenFormat(rawToken);

        String tokenHash =
                refreshTokenGenerator.hash(rawToken);

        lockTokenOwner(tokenHash);

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidToken);

        refreshToken.revoke(
                Instant.now(),
                RefreshTokenRevocationReason.REVOKED
        );
    }

    // ========================================
    // 4. 로그아웃 처리
    // ========================================

    @Transactional
    public void logout(String rawToken) {

        // 쿠키 누락 또는 잘못된 토큰 형식
        if (!hasValidTokenFormat(rawToken)) {
            return;
        }

        String tokenHash =
                refreshTokenGenerator.hash(rawToken);

        UUID userId = refreshTokenRepository
                .findUserIdByTokenHash(tokenHash)
                .orElse(null);

        if (userId == null) {
            return;
        }

        // 사용자 -> Refresh Token 잠금 순서 유지
        User user = userRepository
                .findByIdForUpdate(userId)
                .orElse(null);

        if (user == null) {
            return;
        }

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElse(null);

        if (refreshToken == null) {
            return;
        }

        Instant now = Instant.now();

        // 정상 토큰 또는 유예 시간 내 Rotation 토큰만 허용
        // 만료, 계정 잠금, loginVersion 불일치 검증
        if (!refreshToken.canLogout(
                now,
                LOGOUT_ROTATION_GRACE_PERIOD
        )) {
            return;
        }

        // 정상 토큰이라면 LOGOUT 사유로 폐기
        // 이미 Rotation된 토큰은 최초 폐기 사유 유지
        if (!refreshToken.isRevoked()) {

            refreshToken.revoke(
                    now,
                    RefreshTokenRevocationReason.LOGOUT
            );
        }

        // 동일 로그인 버전의 Access Token과
        // Refresh Token을 모두 논리적으로 무효화
        user.increaseLoginVersion();
    }

    // ========================================
    // 5. 신규 토큰 생성 및 해시 저장
    // ========================================

    private IssuedRefreshToken createToken(
            User user,
            Instant createdAt,
            Instant expiresAt
    ) {

        String rawToken =
                refreshTokenGenerator.generate();

        String tokenHash =
                refreshTokenGenerator.hash(rawToken);

        RefreshToken refreshToken = new RefreshToken(
                user,
                tokenHash,
                createdAt,
                expiresAt
        );

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(
                user.getId(),
                rawToken,
                expiresAt
        );
    }

    // ========================================
    // 6. 사용자 -> Refresh Token 순서로 잠금 획득
    // ========================================

    private User lockTokenOwner(String tokenHash) {

        UUID userId = refreshTokenRepository
                .findUserIdByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);

        return userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(this::invalidToken);
    }

    // ========================================
    // 7. Refresh Token 형식 검증
    // ========================================

    private boolean hasValidTokenFormat(String rawToken) {

        return rawToken != null
                && rawToken.matches("[A-Za-z0-9_-]{43}");
    }

    private void validateTokenFormat(String rawToken) {

        if (!hasValidTokenFormat(rawToken)) {
            throw invalidToken();
        }
    }

    private BadCredentialsException invalidToken() {

        return new BadCredentialsException(
                INVALID_TOKEN_MESSAGE
        );
    }

    // ========================================
    // 8. 발급된 토큰의 내부 전달용 객체
    // ========================================

    public record IssuedRefreshToken(
            UUID userId,
            String rawToken,
            Instant expiresAt
    ) {

        @Override
        public String toString() {

            return "IssuedRefreshToken[REDACTED]";
        }
    }
}