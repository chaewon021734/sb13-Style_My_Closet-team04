package com.project.sb13ootbsteam04.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryPasswordRateLimitFilterTest {

    private static final String TARGET_PATH =
            "/api/auth/temporary-password";

    private TemporaryPasswordRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        // 테스트마다 새로운 필터를 생성해 요청 횟수 초기화
        filter = new TemporaryPasswordRateLimitFilter();
    }

    private MockHttpServletRequest createRequest(
            String method,
            String path,
            String ip
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest(method, path);

        request.setServletPath(path);
        request.setRemoteAddr(ip);

        return request;
    }

    private MockHttpServletResponse executeRequest(
            String method,
            String path,
            String ip,
            FilterChain chain
    ) throws Exception {

        MockHttpServletRequest request =
                createRequest(method, path, ip);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        return response;
    }

    // ========================================
    // 1. 첫 번째 요청 정상 통과
    // ========================================

    @Test
    void shouldAllowFirstRequest() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = executeRequest(
                "POST",
                TARGET_PATH,
                "192.168.0.1",
                chain
        );

        assertEquals(200, response.getStatus());

        verify(chain).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 2. 5회 요청까지 정상 허용
    // ========================================

    @Test
    void shouldAllowFiveRequests() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {

            MockHttpServletResponse response = executeRequest(
                    "POST",
                    TARGET_PATH,
                    "192.168.0.2",
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(5)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 3. 6번째 요청은 429 차단
    // ========================================

    @Test
    void shouldRejectSixthRequest() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {

            executeRequest(
                    "POST",
                    TARGET_PATH,
                    "192.168.0.3",
                    chain
            );
        }

        MockHttpServletResponse response = executeRequest(
                "POST",
                TARGET_PATH,
                "192.168.0.3",
                chain
        );

        assertEquals(429, response.getStatus());

        assertNotNull(
                response.getHeader("Retry-After")
        );

        // 차단된 요청은 Controller로 전달되지 않음
        verify(chain, times(5)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 4. 다른 IP는 요청 횟수 별도 계산
    // ========================================

    @Test
    void shouldTrackDifferentIpsSeparately() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        // 첫 번째 IP가 5회 요청
        for (int i = 0; i < 5; i++) {

            executeRequest(
                    "POST",
                    TARGET_PATH,
                    "192.168.0.4",
                    chain
            );
        }

        // 다른 IP는 첫 번째 요청이므로 허용
        MockHttpServletResponse response = executeRequest(
                "POST",
                TARGET_PATH,
                "192.168.0.5",
                chain
        );

        assertEquals(200, response.getStatus());

        verify(chain, times(6)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 5. 다른 API는 제한하지 않음
    // ========================================

    @Test
    void shouldNotLimitOtherEndpoints() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {

            MockHttpServletResponse response = executeRequest(
                    "POST",
                    "/api/auth/login",
                    "192.168.0.6",
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(10)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }

    // ========================================
    // 6. GET 요청은 제한하지 않음
    // ========================================

    @Test
    void shouldNotLimitGetRequests() throws Exception {

        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {

            MockHttpServletResponse response = executeRequest(
                    "GET",
                    TARGET_PATH,
                    "192.168.0.7",
                    chain
            );

            assertEquals(200, response.getStatus());
        }

        verify(chain, times(10)).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class)
        );
    }
}
