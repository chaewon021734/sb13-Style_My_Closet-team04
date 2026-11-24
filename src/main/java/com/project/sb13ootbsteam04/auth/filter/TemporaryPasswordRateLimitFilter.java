package com.project.sb13ootbsteam04.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TemporaryPasswordRateLimitFilter
        extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 5;

    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    private static final String TARGET_PATH =
            "/api/auth/temporary-password";

    private final ConcurrentHashMap<String, RequestWindow>
            requestCounts = new ConcurrentHashMap<>();

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

        String clientIp = request.getRemoteAddr();
        Instant now = Instant.now();

        RequestWindow window = requestCounts.compute(
                clientIp,
                (ip, previous) -> {

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

        // 5회 초과 요청 차단
        if (window.count() > MAX_REQUESTS) {

            long retryAfter = Math.max(
                    1L,
                    Duration.between(
                            now,
                            window.startedAt().plus(WINDOW)
                    ).toSeconds()
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

        filterChain.doFilter(request, response);
    }

    private record RequestWindow(
            Instant startedAt,
            int count
    ) {
    }
}
