package com.project.sb13ootbsteam04.user.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTemporaryPasswordTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedOriginalPassword"
        );
    }

    // 1. 임시 비밀번호 발급 시 정보 저장
    @Test
    void shouldIssueTemporaryPassword() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");
        Instant expiresAt = now.plusSeconds(180);

        // when
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                expiresAt
        );

        // then
        assertEquals(
                "encodedTemporaryPassword",
                user.getTemporaryPasswordHash()
        );

        assertEquals(
                expiresAt,
                user.getTemporaryPasswordExpiresAt()
        );

        // 기존 비밀번호는 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );
    }

    // 2. 임시 비밀번호가 3분 이내면 유효
    @Test
    void shouldAcceptTemporaryPasswordBeforeExpiration() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                now.plusSeconds(180)
        );

        // when
        boolean active = user.isTemporaryPasswordActive(
                now.plusSeconds(179)
        );

        // then
        assertTrue(active);
    }

    // 3. 정확히 만료 시간이 되면 무효
    @Test
    void shouldRejectTemporaryPasswordAtExpiration() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                now.plusSeconds(180)
        );

        // when
        boolean active = user.isTemporaryPasswordActive(
                now.plusSeconds(180)
        );

        // then
        assertFalse(active);
    }

    // 4. 만료 시간이 지난 임시 비밀번호는 무효
    @Test
    void shouldRejectExpiredTemporaryPassword() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                now.plusSeconds(180)
        );

        // when
        boolean active = user.isTemporaryPasswordActive(
                now.plusSeconds(181)
        );

        // then
        assertFalse(active);
    }

    // 5. 임시 비밀번호 정보 삭제
    @Test
    void shouldClearTemporaryPassword() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                now.plusSeconds(180)
        );

        // when
        user.clearTemporaryPassword();

        // then
        assertNull(user.getTemporaryPasswordHash());
        assertNull(user.getTemporaryPasswordExpiresAt());

        assertFalse(user.isTemporaryPasswordActive(now));
    }

    // 6. 비밀번호 변경 시 임시 비밀번호 삭제 및 JWT 무효화
    @Test
    void shouldClearTemporaryPasswordAndInvalidateJwtWhenPasswordChanges() {

        // given
        Instant now = Instant.parse("2026-11-19T06:00:00Z");

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                now.plusSeconds(180)
        );

        user.increaseLoginVersion();
        long previousVersion = user.getLoginVersion();

        // when
        user.changePassword("encodedNewPassword");

        // then
        assertEquals(
                "encodedNewPassword",
                user.getPassword()
        );

        assertNull(user.getTemporaryPasswordHash());
        assertNull(user.getTemporaryPasswordExpiresAt());

        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );
    }

    // 7. 잘못된 임시 비밀번호 정보 저장 차단
    @Test
    void shouldRejectBlankTemporaryPassword() {

        // given
        Instant expiresAt = Instant.now().plusSeconds(180);

        // when & then
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> user.issueTemporaryPassword("", expiresAt)
        );

        assertEquals(
                "임시 비밀번호는 필수입니다.",
                exception.getMessage()
        );
    }
}
