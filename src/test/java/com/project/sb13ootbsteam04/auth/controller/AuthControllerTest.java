package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // 1. 정상 로그인 → 200 OK
    @Test
    void shouldReturn200WhenLoginSucceeds() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(
                        "test.jwt.token",
                        "Bearer"
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("test.jwt.token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"));
    }

    // 2. 존재하지 않는 이메일 → 401
    @Test
    void shouldReturn401WhenEmailDoesNotExist() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // 3. 비밀번호 오류 → 401
    @Test
    void shouldReturn401WhenPasswordIsWrong() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // 4. 잠긴 계정 → 401
    @Test
    void shouldReturn401WhenAccountIsLocked() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new LockedException("잠긴 계정입니다."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    // 5. 이메일 형식 오류 → 400
    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
