package com.project.sb13ootbsteam04.auth.entity;

import com.project.sb13ootbsteam04.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenTest {
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-11-28T00:00:00Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plusSeconds(604800);
    private static final Instant ROTATED_AT = CREATED_AT.plusSeconds(60);
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(5);
    private User user;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        user = new User("테스트사용자", "test@example.com", "encodedPassword");
        refreshToken = new RefreshToken(user, TOKEN_HASH, CREATED_AT, EXPIRES_AT);
    }

    // 기존 테스트 1~23
    @Test
    void shouldCreateRefreshToken() {
        assertEquals(user, refreshToken.getUser());
        assertEquals(TOKEN_HASH, refreshToken.getTokenHash());
        assertEquals(user.getLoginVersion(), refreshToken.getLoginVersion());
        assertEquals(CREATED_AT, refreshToken.getCreatedAt());
        assertEquals(EXPIRES_AT, refreshToken.getExpiresAt());
        assertNull(refreshToken.getRevokedAt());
        assertFalse(refreshToken.isRevoked());
    }
    @Test
    void shouldBeValidBeforeExpiration() {
        assertTrue(refreshToken.isValid(CREATED_AT.plusSeconds(60)));
        assertFalse(refreshToken.isExpired(CREATED_AT.plusSeconds(60)));
    }
    @Test
    void shouldBeValidImmediatelyBeforeExpiration() {
        assertTrue(refreshToken.isValid(EXPIRES_AT.minusNanos(1)));
    }
    @Test
    void shouldExpireAtExpirationTime() {
        assertTrue(refreshToken.isExpired(EXPIRES_AT));
        assertFalse(refreshToken.isValid(EXPIRES_AT));
    }
    @Test
    void shouldRejectExpiredToken() {
        Instant now = EXPIRES_AT.plusSeconds(1);
        assertTrue(refreshToken.isExpired(now));
        assertFalse(refreshToken.isValid(now));
    }
    @Test
    void shouldRevokeRefreshToken() {
        Instant revokedAt = CREATED_AT.plusSeconds(60);
        refreshToken.revoke(revokedAt);
        assertTrue(refreshToken.isRevoked());
        assertEquals(revokedAt, refreshToken.getRevokedAt());
        assertFalse(refreshToken.isValid(revokedAt));
    }
    @Test
    void shouldPreserveFirstRevocationTime() {
        Instant firstRevokedAt = CREATED_AT.plusSeconds(60);
        Instant secondRevokedAt = CREATED_AT.plusSeconds(120);
        refreshToken.revoke(firstRevokedAt);
        refreshToken.revoke(secondRevokedAt);
        assertEquals(firstRevokedAt, refreshToken.getRevokedAt());
    }
    @Test
    void shouldHaveValidLoginVersion() {
        assertTrue(refreshToken.hasValidLoginVersion());
    }
    @Test
    void shouldRejectTokenWhenLoginVersionChanges() {
        user.increaseLoginVersion();
        assertFalse(refreshToken.hasValidLoginVersion());
        assertFalse(refreshToken.isValid(CREATED_AT.plusSeconds(60)));
    }
    @Test
    void shouldRejectTokenWhenUserIsLocked() {
        user.changeLocked(true);
        assertTrue(user.isLocked());
        assertFalse(refreshToken.isValid(CREATED_AT.plusSeconds(60)));
    }
    @Test
    void shouldRejectOldTokenAfterAccountIsUnlocked() {
        user.changeLocked(true);
        user.changeLocked(false);
        assertFalse(user.isLocked());
        assertFalse(refreshToken.hasValidLoginVersion());
        assertFalse(refreshToken.isValid(CREATED_AT.plusSeconds(60)));
    }
    @Test
    void shouldRejectTokenAfterPasswordChange() {
        user.changePassword("newEncodedPassword");
        assertFalse(refreshToken.hasValidLoginVersion());
        assertFalse(refreshToken.isValid(CREATED_AT.plusSeconds(60)));
    }
    @Test
    void shouldRejectNullUser() {
        assertThrows(NullPointerException.class, () -> new RefreshToken(null, TOKEN_HASH, CREATED_AT, EXPIRES_AT));
    }
    @Test
    void shouldRejectNullTokenHash() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken(user, null, CREATED_AT, EXPIRES_AT));
    }
    @Test
    void shouldRejectInvalidHashLength() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken(user, "a".repeat(63), CREATED_AT, EXPIRES_AT));
    }
    @Test
    void shouldRejectInvalidHashFormat() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken(user, "z".repeat(64), CREATED_AT, EXPIRES_AT));
    }
    @Test
    void shouldRejectEqualCreationAndExpirationTime() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken(user, TOKEN_HASH, CREATED_AT, CREATED_AT));
    }
    @Test
    void shouldRejectExpirationBeforeCreation() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken(user, TOKEN_HASH, CREATED_AT, CREATED_AT.minusSeconds(1)));
    }
    @Test
    void shouldRejectNullCreatedAt() {
        assertThrows(NullPointerException.class, () -> new RefreshToken(user, TOKEN_HASH, null, EXPIRES_AT));
    }
    @Test
    void shouldRejectNullExpiresAt() {
        assertThrows(NullPointerException.class, () -> new RefreshToken(user, TOKEN_HASH, CREATED_AT, null));
    }
    @Test
    void shouldRejectRevocationBeforeCreation() {
        assertThrows(IllegalArgumentException.class, () -> refreshToken.revoke(CREATED_AT.minusSeconds(1)));
        assertFalse(refreshToken.isRevoked());
    }
    @Test
    void shouldRejectNullRevocationTime() {
        assertThrows(NullPointerException.class, () -> refreshToken.revoke(null));
        assertFalse(refreshToken.isRevoked());
    }
    @Test
    void shouldRejectNullCurrentTime() {
        assertThrows(NullPointerException.class, () -> refreshToken.isExpired(null));
        assertThrows(NullPointerException.class, () -> refreshToken.isValid(null));
    }

    // 신규 테스트 24~44: 폐기 사유 및 로그아웃 유예 정책
    @Test
    void shouldHaveNullRevocationReasonOnCreation() {
        assertNull(refreshToken.getRevocationReason());
        assertFalse(refreshToken.isRotated());
    }
    @Test
    void shouldRecordGeneralRevocationReason() {
        refreshToken.revoke(ROTATED_AT);
        assertEquals(RefreshTokenRevocationReason.REVOKED, refreshToken.getRevocationReason());
    }
    @Test
    void shouldRecordRotationReason() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertEquals(RefreshTokenRevocationReason.ROTATED, refreshToken.getRevocationReason());
        assertEquals(ROTATED_AT, refreshToken.getRevokedAt());
    }
    @Test
    void shouldRecordLogoutReason() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.LOGOUT);
        assertEquals(RefreshTokenRevocationReason.LOGOUT, refreshToken.getRevocationReason());
    }
    @Test
    void shouldPreserveFirstRevocationReason() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        refreshToken.revoke(ROTATED_AT.plusSeconds(1), RefreshTokenRevocationReason.LOGOUT);
        assertEquals(RefreshTokenRevocationReason.ROTATED, refreshToken.getRevocationReason());
        assertEquals(ROTATED_AT, refreshToken.getRevokedAt());
    }
    @Test
    void shouldRejectNullRevocationReason() {
        assertThrows(NullPointerException.class, () -> refreshToken.revoke(ROTATED_AT, null));
        assertFalse(refreshToken.isRevoked());
        assertNull(refreshToken.getRevocationReason());
    }
    @Test
    void shouldRecognizeRotatedToken() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertTrue(refreshToken.isRotated());
        assertFalse(refreshToken.isValid(ROTATED_AT));
    }
    @Test
    void shouldNotRecognizeGenerallyRevokedTokenAsRotated() {
        refreshToken.revoke(ROTATED_AT);
        assertFalse(refreshToken.isRotated());
    }
    @Test
    void shouldAllowLogoutImmediatelyAfterRotation() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertTrue(refreshToken.isRecentlyRotated(ROTATED_AT, GRACE_PERIOD));
        assertTrue(refreshToken.canLogout(ROTATED_AT, GRACE_PERIOD));
    }
    @Test
    void shouldAllowLogoutFourSecondsAfterRotation() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertTrue(refreshToken.canLogout(ROTATED_AT.plusSeconds(4), GRACE_PERIOD));
        assertTrue(refreshToken.canLogout(ROTATED_AT.plus(GRACE_PERIOD).minusNanos(1), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutExactlyAtGracePeriodBoundary() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertFalse(refreshToken.isRecentlyRotated(ROTATED_AT.plus(GRACE_PERIOD), GRACE_PERIOD));
        assertFalse(refreshToken.canLogout(ROTATED_AT.plus(GRACE_PERIOD), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutAfterGracePeriod() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertFalse(refreshToken.canLogout(ROTATED_AT.plusSeconds(6), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutBeforeRotationTime() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertFalse(refreshToken.isRecentlyRotated(ROTATED_AT.minusNanos(1), GRACE_PERIOD));
        assertFalse(refreshToken.canLogout(ROTATED_AT.minusNanos(1), GRACE_PERIOD));
    }
    @Test
    void shouldAllowLogoutWithActiveToken() {
        assertTrue(refreshToken.canLogout(CREATED_AT.plusSeconds(60), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutWithExpiredRotatedToken() {
        refreshToken.revoke(EXPIRES_AT.minusSeconds(1), RefreshTokenRevocationReason.ROTATED);
        assertTrue(refreshToken.isRecentlyRotated(EXPIRES_AT, GRACE_PERIOD));
        assertFalse(refreshToken.canLogout(EXPIRES_AT, GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutWithOutdatedLoginVersion() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        user.increaseLoginVersion();
        assertFalse(refreshToken.canLogout(ROTATED_AT.plusSeconds(1), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutWithLockedUser() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        user.changeLocked(true);
        assertFalse(refreshToken.canLogout(ROTATED_AT.plusSeconds(1), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutWithGenerallyRevokedToken() {
        refreshToken.revoke(ROTATED_AT);
        assertFalse(refreshToken.canLogout(ROTATED_AT.plusSeconds(1), GRACE_PERIOD));
    }
    @Test
    void shouldRejectLogoutWithLoggedOutToken() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.LOGOUT);
        assertFalse(refreshToken.canLogout(ROTATED_AT.plusSeconds(1), GRACE_PERIOD));
    }
    @Test
    void shouldRejectZeroGracePeriod() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertThrows(IllegalArgumentException.class, () -> refreshToken.isRecentlyRotated(ROTATED_AT, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> refreshToken.canLogout(ROTATED_AT, Duration.ZERO));
    }
    @Test
    void shouldRejectNegativeGracePeriod() {
        refreshToken.revoke(ROTATED_AT, RefreshTokenRevocationReason.ROTATED);
        assertThrows(IllegalArgumentException.class, () -> refreshToken.isRecentlyRotated(ROTATED_AT, Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> refreshToken.canLogout(ROTATED_AT, Duration.ofSeconds(-1)));
    }
}
