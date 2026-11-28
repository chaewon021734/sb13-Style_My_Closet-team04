package com.project.sb13ootbsteam04.auth.entity;

import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64,
            updatable = false
    )
    private String tokenHash;

    @Column(
            name = "login_version",
            nullable = false,
            updatable = false
    )
    private long loginVersion;

    @Column(
            name = "expires_at",
            nullable = false,
            updatable = false
    )
    private Instant expiresAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    // Refresh Token 폐기 사유
    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 20)
    private RefreshTokenRevocationReason revocationReason;

    public RefreshToken(
            User user,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.user = Objects.requireNonNull(
                user,
                "사용자는 필수입니다."
        );

        if (tokenHash == null
                || !tokenHash.matches("[0-9a-f]{64}")) {

            throw new IllegalArgumentException(
                    "Refresh Token 해시 형식이 올바르지 않습니다."
            );
        }

        this.createdAt = Objects.requireNonNull(
                createdAt,
                "토큰 발급 시간은 필수입니다."
        );

        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "토큰 만료 시간은 필수입니다."
        );

        if (!expiresAt.isAfter(createdAt)) {

            throw new IllegalArgumentException(
                    "토큰 만료 시간은 발급 시간보다 이후여야 합니다."
            );
        }

        this.tokenHash = tokenHash;
        this.loginVersion = user.getLoginVersion();
        this.revokedAt = null;
        this.revocationReason = null;
    }

    // ========================================
    // 1. 토큰 상태 검증
    // ========================================

    // 토큰 만료 여부 확인
    public boolean isExpired(Instant now) {

        Objects.requireNonNull(
                now,
                "현재 시간은 필수입니다."
        );

        return !now.isBefore(expiresAt);
    }

    // 토큰 폐기 여부 확인
    public boolean isRevoked() {

        return revokedAt != null;
    }

    // 사용자 로그인 버전 일치 여부 확인
    public boolean hasValidLoginVersion() {

        return loginVersion == user.getLoginVersion();
    }

    // Refresh Token 사용 가능 여부 확인
    public boolean isValid(Instant now) {

        return !isExpired(now)
                && !isRevoked()
                && !user.isLocked()
                && hasValidLoginVersion();
    }

    // ========================================
    // 2. 토큰 폐기 사유 검증
    // ========================================

    // Rotation으로 폐기된 토큰인지 확인
    public boolean isRotated() {

        return isRevoked()
                && revocationReason
                == RefreshTokenRevocationReason.ROTATED;
    }

    // 허용된 유예 시간 이내에 갱신된 토큰인지 확인
    public boolean isRecentlyRotated(
            Instant now,
            Duration gracePeriod
    ) {

        Objects.requireNonNull(
                now,
                "현재 시간은 필수입니다."
        );

        Objects.requireNonNull(
                gracePeriod,
                "유예 시간은 필수입니다."
        );

        if (gracePeriod.isNegative()
                || gracePeriod.isZero()) {

            throw new IllegalArgumentException(
                    "유예 시간은 0보다 커야 합니다."
            );
        }

        if (!isRotated()) {
            return false;
        }

        if (now.isBefore(revokedAt)) {
            return false;
        }

        return Duration.between(revokedAt, now)
                .compareTo(gracePeriod) < 0;
    }

    // 로그아웃 처리 가능 여부 확인
    public boolean canLogout(
            Instant now,
            Duration gracePeriod
    ) {

        if (isExpired(now)
                || user.isLocked()
                || !hasValidLoginVersion()) {

            return false;
        }

        // 아직 폐기되지 않은 정상 토큰
        if (!isRevoked()) {
            return true;
        }

        // 최근 Rotation으로 폐기된 동일 로그인 세션 토큰
        return isRecentlyRotated(
                now,
                gracePeriod
        );
    }

    // ========================================
    // 3. Refresh Token 폐기
    // ========================================

    // 기존 폐기 메서드 유지
    public void revoke(Instant now) {

        revoke(
                now,
                RefreshTokenRevocationReason.REVOKED
        );
    }

    // 폐기 사유를 포함한 토큰 폐기
    public void revoke(
            Instant now,
            RefreshTokenRevocationReason reason
    ) {

        Objects.requireNonNull(
                now,
                "토큰 폐기 시간은 필수입니다."
        );

        Objects.requireNonNull(
                reason,
                "토큰 폐기 사유는 필수입니다."
        );

        if (now.isBefore(createdAt)) {

            throw new IllegalArgumentException(
                    "토큰 폐기 시간은 발급 시간보다 이전일 수 없습니다."
            );
        }

        // 최초 폐기 시간과 폐기 사유 유지
        if (!isRevoked()) {

            this.revokedAt = now;
            this.revocationReason = reason;
        }
    }
}