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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    // 동일 IP에서 10분 동안 최대 20회 로그인 요청
    private static final int MAX_REQUESTS = 20;

    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    // 100회 요청마다 만료 기록 정리
    private static final long CLEANUP_INTERVAL = 100L;

    private static final String TARGET_PATH =
            "/api/auth/login";

    private final Clock clock;

    // IP별 로그인 요청 기록
    private final ConcurrentHashMap<String, RequestWindow>
            requestCounts = new ConcurrentHashMap<>();

    private final AtomicLong requestCounter =
            new AtomicLong();

    // 실제 애플리케이션에서 사용하는 생성자
    @Autowired
    public LoginRateLimitFilter() {
        this(Clock.systemUTC());
    }

    // 단위 테스트에서 시간을 조절하기 위한 생성자
    LoginRateLimitFilter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        // 로그인 POST 요청에만 제한 적용
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

        // 1. 클라이언트 IP 확인
        String clientIp = request.getRemoteAddr();

        // 2. 현재 시간
        Instant now = clock.instant();

        // 3. IP별 요청 횟수 계산
        RequestWindow window = requestCounts.compute(
                clientIp,
                (ip, previous) -> {

                    // 첫 요청이거나 10분이 지났다면 초기화
                    if (previous == null
                            || !now.isBefore(
                            previous.startedAt().plus(WINDOW)
                    )) {
                        return new RequestWindow(now, 1);
                    }

                    return new RequestWindow(
                            previous.startedAt(),
                            previous.count() + 1
                    );
                }
        );

        // 4. 오래된 IP 기록 정리
        if (requestCounter.incrementAndGet()
                % CLEANUP_INTERVAL == 0) {
            cleanupExpiredRequests(now);
        }

        // 5. IP별 20회 초과 시 차단
        if (window.count() > MAX_REQUESTS) {

            long remainingMillis = Math.max(
                    0L,
                    Duration.between(
                            now,
                            window.startedAt().plus(WINDOW)
                    ).toMillis()
            );

            long retryAfterSeconds = Math.max(
                    1L,
                    (remainingMillis + 999L) / 1000L
            );

            response.setHeader(
                    "Retry-After",
                    String.valueOf(retryAfterSeconds)
            );

            response.sendError(
                    429,
                    "로그인 요청 횟수가 초과되었습니다."
            );

            return;
        }

        // 6. 제한 이내면 로그인 API로 전달
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
                requestCounts.remove(ip, window);
            }
        });
    }

    // ========================================
    // IP별 요청 기록
    // ========================================

    private record RequestWindow(
            Instant startedAt,
            int count
    ) {
    }
}
