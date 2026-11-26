package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.exception.LoginAttemptBlockedException;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginAttemptBlockedWebTest {

    private static final String LOGIN_URL =
            "/api/auth/login";

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {

        authService = mock(AuthService.class);

        AuthController authController =
                new AuthController(authService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(
                        new GlobalExceptionHandler()
                )
                .build();
    }

    // 로그인 HTTP 요청 생성
    private org.springframework.test.web.servlet.RequestBuilder
    loginRequest() {

        return post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "test@example.com",
                          "password": "password123"
                        }
                        """);
    }

    // ========================================
    // 1. 계정 차단 시 429 + Retry-After 반환
    // ========================================

    @Test
    void shouldReturn429AndRetryAfterWhenAccountBlocked()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(
                        new LoginAttemptBlockedException(
                                "로그인 시도 횟수가 초과되었습니다. 잠시 후 다시 시도해 주세요.",
                                600L
                        )
                );

        // when & then
        mockMvc.perform(loginRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(
                        header().string("Retry-After", "600")
                )
                .andExpect(
                        content().string(
                                containsString(
                                        "LoginAttemptBlockedException"
                                )
                        )
                );

        verify(authService)
                .login(any(LoginRequest.class));
    }

    // ========================================
    // 2. 차단 남은 시간 변경 시 헤더에 반영
    // ========================================

    @Test
    void shouldReturnRemainingLockoutSeconds()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(
                        new LoginAttemptBlockedException(
                                "로그인 시도 횟수가 초과되었습니다.",
                                420L
                        )
                );

        // when & then
        mockMvc.perform(loginRequest())
                .andExpect(status().isTooManyRequests())
                .andExpect(
                        header().string("Retry-After", "420")
                );
    }

    // ========================================
    // 3. 일반 로그인 실패는 기존 401 유지
    // ========================================

    @Test
    void shouldReturn401ForInvalidCredentials()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(
                        new BadCredentialsException(
                                "이메일 또는 비밀번호가 올바르지 않습니다."
                        )
                );

        // when & then
        mockMvc.perform(loginRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content().string(
                                containsString(
                                        "이메일 또는 비밀번호가 올바르지 않습니다."
                                )
                        )
                )
                .andExpect(
                        header().doesNotExist("Retry-After")
                );
    }

    // ========================================
    // 4. 정상 로그인은 기존 200 유지
    // ========================================

    @Test
    void shouldReturn200ForSuccessfulLogin()
            throws Exception {

        // given
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(
                        new LoginResponse(
                                "test.jwt.token",
                                "Bearer"
                        )
                );

        // when & then
        mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("test.jwt.token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                );
    }
}
