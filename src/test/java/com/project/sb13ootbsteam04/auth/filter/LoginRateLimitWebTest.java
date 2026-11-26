package com.project.sb13ootbsteam04.auth.filter;

import com.project.sb13ootbsteam04.auth.controller.AuthController;
import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginRateLimitWebTest {

    private static final String LOGIN_URL =
            "/api/auth/login";

    private static final String TEST_IP =
            "198.51.100.30";

    private MockMvc mockMvc;

    private AuthService authService;

    @BeforeEach
    void setUp() {

        // AuthService는 Mock으로 처리
        authService = mock(AuthService.class);

        AuthController authController =
                new AuthController(authService);

        LoginRateLimitFilter loginRateLimitFilter =
                new LoginRateLimitFilter();

        // 실제 Controller + Rate Limit Filter 연결
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .addFilters(loginRateLimitFilter)
                .build();
    }

    // ========================================
    // 로그인 요청 생성
    // ========================================

    private org.springframework.test.web.servlet.RequestBuilder
    loginRequest(String ip) {

        return post(LOGIN_URL)
                .servletPath(LOGIN_URL)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "test@example.com",
                          "password": "password123"
                        }
                        """);
    }

    // ========================================
    // 1. 20회 요청 허용, 21번째 요청 차단
    // ========================================

    @Test
    void shouldAllowTwentyRequestsAndRejectTwentyFirst()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(
                        new LoginResponse(
                                "test.jwt.token",
                                "Bearer"
                        )
                );

        // 1~20번째 로그인 요청
        for (int i = 0; i < 20; i++) {

            mockMvc.perform(loginRequest(TEST_IP))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken")
                            .value("test.jwt.token"))
                    .andExpect(jsonPath("$.tokenType")
                            .value("Bearer"));
        }

        // 21번째 로그인 요청
        mockMvc.perform(loginRequest(TEST_IP))
                .andExpect(status().isTooManyRequests())
                .andExpect(header()
                        .string("Retry-After", "600"));

        // Controller는 20회까지만 호출
        verify(authService, times(20))
                .login(any(LoginRequest.class));
    }

    // ========================================
    // 2. 다른 IP는 별도 관리
    // ========================================

    @Test
    void shouldTrackDifferentIpsSeparately()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(
                        new LoginResponse(
                                "test.jwt.token",
                                "Bearer"
                        )
                );

        // 첫 번째 IP에서 20회 로그인
        for (int i = 0; i < 20; i++) {

            mockMvc.perform(loginRequest(TEST_IP))
                    .andExpect(status().isOk());
        }

        // 첫 번째 IP의 21번째 요청은 차단
        mockMvc.perform(loginRequest(TEST_IP))
                .andExpect(status().isTooManyRequests());

        // 다른 IP의 로그인 요청은 허용
        mockMvc.perform(
                        loginRequest("198.51.100.40")
                )
                .andExpect(status().isOk());

        verify(authService, times(21))
                .login(any(LoginRequest.class));
    }

    // ========================================
    // 3. 비로그인 API에는 제한 미적용
    // ========================================

    @Test
    void shouldNotRateLimitOtherEndpoints()
            throws Exception {

        // 로그인 API와 다른 경로로 요청
        // standaloneSetup에는 해당 Controller가 없으므로
        // 404를 반환하지만 429는 반환하지 않아야 함.

        for (int i = 0; i < 25; i++) {

            mockMvc.perform(
                            post("/api/auth/other")
                                    .servletPath("/api/auth/other")
                                    .with(request -> {
                                        request.setRemoteAddr(TEST_IP);
                                        return request;
                                    })
                    )
                    .andExpect(status().isNotFound());
        }

        verifyNoInteractions(authService);
    }
}
