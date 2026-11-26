package com.project.sb13ootbsteam04.auth.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginAttemptService {

    // 로그인 실패 허용 횟수
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // 실패 횟수 집계 및 차단 시간
    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    // 만료된 실패 기록 정리 주기
    private static final int CLEANUP_INTERVAL = 100;

    // 계정별 로그인 실패 기록
    private final ConcurrentHashMap<String, AttemptState>
            attempts = new ConcurrentHashMap<>();

    // 정리 주기 카운터
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    private final Clock clock;

    // 실제 애플리케이션에서 사용하는 생성자
    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // 테스트에서 시간을 제어하기 위한 생성자
    LoginAttemptService(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    // ========================================
    // 로그인 시도 가능 여부 확인
    // ========================================

    public boolean isBlocked(String email) {

        String key = normalizeEmail(email);
        Instant now = clock.instant();

        AttemptState state = attempts.get(key);

        if (state == null) {
            return false;
        }

        // 차단 중이라면 로그인 불가
        if (state.blockedUntil() != null) {

            if (now.isBefore(state.blockedUntil())) {
                return true;
            }

            // 차단 시간 만료 시 기존 상태 제거
            attempts.remove(key, state);
            return false;
        }

        // 실패 기록 집계 시간이 만료되었으면 초기화
        if (!now.isBefore(state.startedAt().plus(WINDOW))) {
            attempts.remove(key, state);
        }

        return false;
    }

    // ========================================
    // 로그인 실패 기록
    // ========================================

    public void recordFailure(String email) {

        String key = normalizeEmail(email);
        Instant now = clock.instant();

        attempts.compute(key, (ignored, previous) -> {

            // 기록이 없거나 기존 기간이 만료된 경우
            if (previous == null
                    || (previous.blockedUntil() == null
                    && !now.isBefore(
                    previous.startedAt().plus(WINDOW)
            ))) {

                return new AttemptState(now, 1, null);
            }

            // 이미 차단 중이라면 기존 상태 유지
            if (previous.blockedUntil() != null) {

                if (now.isBefore(previous.blockedUntil())) {
                    return previous;
                }

                // 차단 해제 후 새로운 실패로 계산
                return new AttemptState(now, 1, null);
            }

            int failedCount = previous.failedCount() + 1;

            // 5번째 실패부터 10분 동안 차단
            if (failedCount >= MAX_FAILED_ATTEMPTS) {

                return new AttemptState(
                        previous.startedAt(),
                        failedCount,
                        now.plus(WINDOW)
                );
            }

            return new AttemptState(
                    previous.startedAt(),
                    failedCount,
                    null
            );
        });

        // 일정 횟수마다 만료된 실패 기록 정리
        if (cleanupCounter.incrementAndGet() >= CLEANUP_INTERVAL) {

            if (cleanupCounter.getAndSet(0) >= CLEANUP_INTERVAL) {
                cleanupExpiredAttempts();
            }
        }
    }

    // ========================================
    // 만료된 로그인 실패 기록 정리
    // ========================================

    private void cleanupExpiredAttempts() {

        Instant now = clock.instant();

        attempts.forEach((email, state) -> {

            boolean expired;

            // 계정 차단 기록의 만료 여부 확인
            if (state.blockedUntil() != null) {

                expired = !now.isBefore(state.blockedUntil());

            } else {

                // 일반 로그인 실패 기록의 만료 여부 확인
                expired = !now.isBefore(
                        state.startedAt().plus(WINDOW)
                );
            }

            // 현재 상태가 그대로인 경우에만 제거
            if (expired) {
                attempts.remove(email, state);
            }
        });
    }

    // ========================================
    // 로그인 성공 시 실패 기록 초기화
    // ========================================

    public void recordSuccess(String email) {

        String key = normalizeEmail(email);

        attempts.remove(key);
    }

    // ========================================
    // 남은 차단 시간 조회
    // ========================================

    public long getRetryAfterSeconds(String email) {

        String key = normalizeEmail(email);
        AttemptState state = attempts.get(key);

        if (state == null || state.blockedUntil() == null) {
            return 0L;
        }

        long remainingMillis = Duration.between(
                clock.instant(),
                state.blockedUntil()
        ).toMillis();

        if (remainingMillis <= 0) {
            return 0L;
        }

        return (remainingMillis + 999L) / 1000L;
    }

    // ========================================
    // 이메일 형식 정규화
    // ========================================

    private String normalizeEmail(String email) {

        return Objects.requireNonNull(email, "email")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    // ========================================
    // 계정별 로그인 실패 기록
    // ========================================

    private record AttemptState(
            Instant startedAt,
            int failedCount,
            Instant blockedUntil
    ) {
    }
}
