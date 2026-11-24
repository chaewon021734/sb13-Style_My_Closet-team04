package com.project.sb13ootbsteam04.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TemporaryPasswordRateLimitFilter
        extends OncePerRequestFilter {

    // IP별 최대 요청 횟수
    private static final int MAX_REQUESTS = 5;

    // 요청 제한 시간: 10분
    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    // 요청 100회마다 만료 기록 정리
    private static final long CLEANUP_INTERVAL = 100L;

    private static final String TARGET_PATH =
            "/api/auth/temporary-password";

    // 테스트에서 시간을 조절할 수 있도록 Clock 사용
    private final Clock clock;

    // IP별 요청 기록
    private final ConcurrentHashMap<String, RequestWindow>
            requestCounts = new ConcurrentHashMap<>();

    // 전체 요청 횟수
    private final AtomicLong requestCounter =
            new AtomicLong();

    // 실제 Spring Boot 환경에서 사용하는 생성자
    @Autowired
    public TemporaryPasswordRateLimitFilter() {
        this(Clock.systemUTC());
    }

    // 테스트에서 가짜 Clock을 전달하기 위한 생성자
    TemporaryPasswordRateLimitFilter(Clock clock) {
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return !(
                "POST".equalsIgnoreCase(request.getMethod())
                        && TARGET_PATH.equals(request.getServletPath())
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. 현재 요청 IP
        String clientIp = request.getRemoteAddr();

        // 2. Clock을 통해 현재 시간 확인
        Instant now = clock.instant();

        // 3. IP별 요청 횟수 갱신
        RequestWindow window = requestCounts.compute(
                clientIp,
                (ip, previous) -> {

                    // 최초 요청 또는 10분 만료
                    if (previous == null
                            || !now.isBefore(
                            previous.startedAt().plus(WINDOW)
                    )) {
                        return new RequestWindow(now, 1);
                    }

                    // 기존 요청 횟수 증가
                    return new RequestWindow(
                            previous.startedAt(),
                            previous.count() + 1
                    );
                }
        );

        // 4. 100회 요청마다 만료 기록 정리
        if (requestCounter.incrementAndGet()
                % CLEANUP_INTERVAL == 0) {
            cleanupExpiredRequests(now);
        }

        // 5. 최대 5회 초과 요청 차단
        if (window.count() > MAX_REQUESTS) {

            long remainingMillis = Math.max(
                    0L,
                    Duration.between(
                            now,
                            window.startedAt().plus(WINDOW)
                    ).toMillis()
            );

            long retryAfter = Math.max(
                    1L,
                    (remainingMillis + 999L) / 1000L
            );

            response.setHeader(
                    "Retry-After",
                    String.valueOf(retryAfter)
            );

            response.sendError(
                    429,
                    "요청 횟수가 초과되었습니다."
            );

            return;
        }

        // 6. 정상 요청은 다음 필터 및 Controller로 전달
        filterChain.doFilter(request, response);
    }

    // ========================================
    // 만료된 IP 요청 기록 정리
    // ========================================

    private void cleanupExpiredRequests(Instant now) {

        requestCounts.forEach((ip, window) -> {

            boolean expired = !now.isBefore(
                    window.startedAt().plus(WINDOW)
            );

            if (expired) {
                // 다른 요청이 갱신한 기록은 삭제하지 않음
                requestCounts.remove(ip, window);
            }
        });
    }

    // ========================================
    // 요청 기록
    // ========================================

    private record RequestWindow(
            Instant startedAt,
            int count
    ) {
    }
}
