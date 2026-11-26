package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    private MutableClock clock;
    private LoginAttemptService loginAttemptService;

    private static final String EMAIL =
            "test@example.com";

    @BeforeEach
    void setUp() {

        clock = new MutableClock(
                Instant.parse("2026-11-24T00:00:00Z")
        );

        loginAttemptService =
                new LoginAttemptService(clock);
    }

    // ========================================
    // 1. 4회 실패까지 로그인 허용
    // ========================================

    @Test
    void shouldAllowLoginBeforeFiveFailures() {

        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertFalse(
                loginAttemptService.isBlocked(EMAIL)
        );
    }

    // ========================================
    // 2. 5회 실패 시 10분 차단
    // ========================================

    @Test
    void shouldBlockAfterFiveFailures() {

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertTrue(
                loginAttemptService.isBlocked(EMAIL)
        );

        assertEquals(
                600L,
                loginAttemptService.getRetryAfterSeconds(EMAIL)
        );
    }

    // ========================================
    // 3. 차단 중 추가 실패에도 차단 유지
    // ========================================

    @Test
    void shouldKeepBlockedDuringLockout() {

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        clock.advance(Duration.ofMinutes(3));

        loginAttemptService.recordFailure(EMAIL);

        assertTrue(
                loginAttemptService.isBlocked(EMAIL)
        );

        assertEquals(
                420L,
                loginAttemptService.getRetryAfterSeconds(EMAIL)
        );
    }

    // ========================================
    // 4. 10분 경과 후 자동 해제
    // ========================================

    @Test
    void shouldUnblockAfterTenMinutes() {

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertTrue(
                loginAttemptService.isBlocked(EMAIL)
        );

        clock.advance(Duration.ofMinutes(10));

        assertFalse(
                loginAttemptService.isBlocked(EMAIL)
        );

        assertEquals(
                0L,
                loginAttemptService.getRetryAfterSeconds(EMAIL)
        );
    }

    // ========================================
    // 5. 로그인 성공 시 실패 횟수 초기화
    // ========================================

    @Test
    void shouldResetFailuresAfterSuccessfulLogin() {

        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        loginAttemptService.recordSuccess(EMAIL);

        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        assertFalse(
                loginAttemptService.isBlocked(EMAIL)
        );
    }

    // ========================================
    // 6. 이메일 대소문자 구분 없이 동일 계정 처리
    // ========================================

    @Test
    void shouldNormalizeEmailCase() {

        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure(
                    "TEST@EXAMPLE.COM"
            );
        }

        loginAttemptService.recordFailure(
                "test@example.com"
        );

        assertTrue(
                loginAttemptService.isBlocked(
                        "Test@Example.com"
                )
        );
    }

    // ========================================
    // 7. 차단 남은 시간 확인
    // ========================================

    @Test
    void shouldCalculateRemainingLockoutTime() {

        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure(EMAIL);
        }

        clock.advance(Duration.ofSeconds(90));

        assertEquals(
                510L,
                loginAttemptService.getRetryAfterSeconds(EMAIL)
        );
    }

    // ========================================
    // 8. 만료된 로그인 실패 기록 주기적 정리
    // ========================================

    @Test
    void shouldCleanupExpiredAttemptsPeriodically() {

        // 서로 다른 이메일 99개에 실패 기록 생성
        for (int i = 0; i < 99; i++) {
            loginAttemptService.recordFailure(
                    "user" + i + "@example.com"
            );
        }

        // 10분 집계 기간이 지나도록 기존 이메일에 재접근하지 않음
        clock.advance(Duration.ofMinutes(11));

        // 100번째 로그인 실패 기록으로 정리 실행
        loginAttemptService.recordFailure(
                "new@example.com"
        );

        // 내부 Map에 신규 기록만 남았는지 확인
        Object field = ReflectionTestUtils.getField(
                loginAttemptService,
                "attempts"
        );

        assertNotNull(field);
        assertInstanceOf(Map.class, field);

        Map<?, ?> attempts = (Map<?, ?>) field;

        assertEquals(1, attempts.size());
        assertTrue(
                attempts.containsKey("new@example.com")
        );
    }

    // ========================================
    // 테스트용 시간 제어 Clock
    // ========================================

    private static class MutableClock extends Clock {

        private Instant currentInstant;

        MutableClock(Instant initialInstant) {
            this.currentInstant = initialInstant;
        }

        void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
