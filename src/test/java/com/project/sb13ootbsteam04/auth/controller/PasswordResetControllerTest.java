package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@Import(SecurityConfig.class)
class PasswordResetControllerTest {

    private static final String URL = "/api/auth/password";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // 테스트마다 사용할 사용자 UUID
    private final UUID userId = UUID.randomUUID();

    // ========================================
    // 테스트용 JWT 생성
    // ========================================

    private Jwt createJwt(String role, String authType) {

        Instant now = Instant.now();

        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(180))
                .claim("role", role)
                .claim("authType", authType)
                .claim("loginVersion", 1L)
                .build();
    }

    // ========================================
    // 1. 임시 JWT로 비밀번호 재설정 성공
    // ========================================

    @Test
    void shouldResetPasswordWithTemporaryJwt() throws Exception {

        // given
        when(jwtDecoder.decode("temporary-token"))
                .thenReturn(createJwt("USER", "TEMPORARY"));

        // when & then
        mockMvc.perform(
                        patch(URL)
                                .header(
                                        "Authorization",
                                        "Bearer temporary-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isNoContent());

        // JWT의 사용자 UUID로 Service 호출 확인
        verify(passwordResetService).resetPassword(
                userId,
                "NewPassword123!"
        );
    }

    // ========================================
    // 2. 일반 USER JWT는 재설정 차단
    // ========================================

    @Test
    void shouldRejectNormalUserJwt() throws Exception {

        // given
        when(jwtDecoder.decode("normal-user-token"))
                .thenReturn(createJwt("USER", "NORMAL"));

        // when & then
        mockMvc.perform(
                        patch(URL)
                                .header(
                                        "Authorization",
                                        "Bearer normal-user-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // ========================================
    // 3. 일반 ADMIN JWT도 재설정 차단
    // ========================================

    @Test
    void shouldRejectNormalAdminJwt() throws Exception {

        // given
        when(jwtDecoder.decode("normal-admin-token"))
                .thenReturn(createJwt("ADMIN", "NORMAL"));

        // when & then
        mockMvc.perform(
                        patch(URL)
                                .header(
                                        "Authorization",
                                        "Bearer normal-admin-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // ========================================
    // 4. JWT가 없으면 인증 실패
    // ========================================

    @Test
    void shouldRejectRequestWithoutJwt() throws Exception {

        // when & then
        mockMvc.perform(
                        patch(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "newPassword": "NewPassword123!"
                                }
                                """)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
        verifyNoInteractions(jwtDecoder);
    }

    // ========================================
    // 5. 새 비밀번호가 너무 짧으면 400
    // ========================================

    @Test
    void shouldRejectShortNewPassword() throws Exception {

        // given
        when(jwtDecoder.decode("temporary-token"))
                .thenReturn(createJwt("USER", "TEMPORARY"));

        // when & then
        mockMvc.perform(
                        patch(URL)
                                .header(
                                        "Authorization",
                                        "Bearer temporary-token"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "newPassword": "1234"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        // DTO 검증 실패로 Service 호출되지 않아야 함
        verifyNoInteractions(passwordResetService);
    }
}
