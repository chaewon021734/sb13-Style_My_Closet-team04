package com.project.sb13ootbsteam04.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginRateLimitFilterTest {

    private static final String LOGIN_PATH =
            "/api/auth/login";

    private static final String DEFAULT_IP =
            "198.51.100.10";

    private MutableClock clock;
    private LoginRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(
                Instant.parse("2026-11-24T00:00:00Z")
        );

        filter = new LoginRateLimitFilter(clock);
    }

    private MockHttpServletResponse performRequest(
            String method,
            String path,
            String ip,
            FilterChain chain
    ) throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);

        request.setServletPath(path);
        request.setRemoteAddr(ip);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        return response;
    }

    // ========================================
    // 1. 첫 번째 로그인 요청 허용
    // ========================================

    @Test
    void shouldAllowFirstLoginRequest() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(200, response.getStatus());

        verify(chain).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 2. 20회까지 요청 허용
    // ========================================

    @Test
    void shouldAllowTwentyRequests() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {

            MockHttpServletResponse response = performRequest(
                    "POST",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(20)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 3. 21번째 요청 차단 및 Retry-After 확인
    // ========================================

    @Test
    void shouldRejectTwentyFirstRequest() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            performRequest(
                    "POST",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );
        }

        MockHttpServletResponse response = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(429, response.getStatus());

        assertEquals(
                "600",
                response.getHeader("Retry-After")
        );

        verify(chain, times(20)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 4. 다른 IP는 별도로 요청 횟수 계산
    // ========================================

    @Test
    void shouldTrackDifferentIpsSeparately() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            performRequest(
                    "POST",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );
        }

        MockHttpServletResponse blockedResponse = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(429, blockedResponse.getStatus());

        MockHttpServletResponse otherIpResponse = performRequest(
                "POST",
                LOGIN_PATH,
                "198.51.100.20",
                chain
        );

        assertEquals(200, otherIpResponse.getStatus());

        verify(chain, times(21)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 5. 10분 경과 후 요청 제한 자동 해제
    // ========================================

    @Test
    void shouldAllowRequestsAfterWindowExpires() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            performRequest(
                    "POST",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );
        }

        // 현재는 21번째 요청 차단
        MockHttpServletResponse blockedResponse = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(429, blockedResponse.getStatus());

        // 10분 경과
        clock.advance(Duration.ofMinutes(10));

        MockHttpServletResponse response = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(200, response.getStatus());

        verify(chain, times(21)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 6. 다른 API 요청은 제한하지 않음
    // ========================================

    @Test
    void shouldNotLimitOtherEndpoints() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 30; i++) {

            MockHttpServletResponse response = performRequest(
                    "POST",
                    "/api/auth/temporary-password",
                    DEFAULT_IP,
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(30)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 7. GET 로그인 경로는 제한하지 않음
    // ========================================

    @Test
    void shouldNotLimitGetRequests() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 30; i++) {

            MockHttpServletResponse response = performRequest(
                    "GET",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(30)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 8. 차단 남은 시간 계산 확인
    // ========================================

    @Test
    void shouldCalculateRetryAfterSeconds() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 20; i++) {
            performRequest(
                    "POST",
                    LOGIN_PATH,
                    DEFAULT_IP,
                    chain
            );
        }

        // 최초 요청으로부터 90초 경과
        clock.advance(Duration.ofSeconds(90));

        MockHttpServletResponse response = performRequest(
                "POST",
                LOGIN_PATH,
                DEFAULT_IP,
                chain
        );

        assertEquals(429, response.getStatus());

        assertEquals(
                "510",
                response.getHeader("Retry-After")
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
