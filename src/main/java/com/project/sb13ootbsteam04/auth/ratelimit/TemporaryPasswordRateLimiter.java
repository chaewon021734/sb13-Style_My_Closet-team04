package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class TemporaryPasswordRateLimiter {

    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    private static final Duration CLEANUP_INTERVAL =
            Duration.ofMinutes(1);

    private static final int EMAIL_LIMIT = 3;
    private static final int IP_LIMIT = 10;

    private final Clock clock;

    private Instant lastCleanupAt;

    private final Map<String, Deque<Instant>> emailRequests =
            new HashMap<>();

    private final Map<String, Deque<Instant>> ipRequests =
            new HashMap<>();

    /**
     * 운영 환경에서는 실제 시스템 시간을 사용한다.
     */
    public TemporaryPasswordRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * 테스트 환경에서는 시간을 직접 제어할 수 있도록 한다.
     */
    TemporaryPasswordRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.lastCleanupAt = clock.instant();
    }

    /**
     * 이메일과 IP별 요청 횟수를 동시에 검사한다.
     * 두 제한 중 하나라도 초과하면 요청을 거절한다.
     */
    public synchronized void checkAndRecord(
            String email,
            String ipAddress
    ) {

        Instant now = clock.instant();
        Instant cutoff = now.minus(WINDOW);

        // 일정 간격으로 만료된 요청 기록과 키를 정리한다.
        cleanupIfNecessary(now, cutoff);

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        String normalizedIp = ipAddress.trim();

        Deque<Instant> emailHistory =
                emailRequests.get(normalizedEmail);

        Deque<Instant> ipHistory =
                ipRequests.get(normalizedIp);

        // 현재 요청에 해당하는 만료 기록 제거
        if (emailHistory != null) {
            removeExpired(emailHistory, cutoff);

            if (emailHistory.isEmpty()) {
                emailRequests.remove(normalizedEmail);
                emailHistory = null;
            }
        }

        if (ipHistory != null) {
            removeExpired(ipHistory, cutoff);

            if (ipHistory.isEmpty()) {
                ipRequests.remove(normalizedIp);
                ipHistory = null;
            }
        }

        // 이메일 또는 IP 중 하나라도 제한 초과
        boolean emailLimitExceeded =
                emailHistory != null
                        && emailHistory.size() >= EMAIL_LIMIT;

        boolean ipLimitExceeded =
                ipHistory != null
                        && ipHistory.size() >= IP_LIMIT;

        if (emailLimitExceeded || ipLimitExceeded) {
            throw new TemporaryPasswordRateLimitException();
        }

        // 제한을 통과한 요청만 키를 생성하고 기록한다.
        emailRequests.computeIfAbsent(
                normalizedEmail,
                key -> new ArrayDeque<>()
        ).addLast(now);

        ipRequests.computeIfAbsent(
                normalizedIp,
                key -> new ArrayDeque<>()
        ).addLast(now);
    }

    /**
     * 매 요청마다 전체 Map을 순회하지 않고,
     * 마지막 정리 시점으로부터 1분이 지났을 때만 정리한다.
     */
    private void cleanupIfNecessary(
            Instant now,
            Instant cutoff
    ) {

        if (now.isBefore(
                lastCleanupAt.plus(CLEANUP_INTERVAL)
        )) {
            return;
        }

        cleanupExpiredEntries(emailRequests, cutoff);
        cleanupExpiredEntries(ipRequests, cutoff);

        lastCleanupAt = now;
    }

    /**
     * 만료된 요청 기록을 제거하고,
     * 남은 기록이 없는 이메일 또는 IP 키를 삭제한다.
     */
    private void cleanupExpiredEntries(
            Map<String, Deque<Instant>> requests,
            Instant cutoff
    ) {

        requests.entrySet().removeIf(entry -> {

            Deque<Instant> history = entry.getValue();

            removeExpired(history, cutoff);

            return history.isEmpty();
        });
    }

    /**
     * 요청 제한 시간인 10분이 지난 기록을 제거한다.
     */
    private void removeExpired(
            Deque<Instant> history,
            Instant cutoff
    ) {

        while (!history.isEmpty()
                && !history.peekFirst().isAfter(cutoff)) {

            history.removeFirst();
        }
    }
}