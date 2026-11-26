package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemporaryPasswordController.class)
@Import(SecurityConfig.class)
class TemporaryPasswordControllerTest {

    private static final String URL =
            "/api/auth/temporary-password";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemporaryPasswordService temporaryPasswordService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ========================================
    // 1. 정상 이메일로 발급 요청
    // ========================================

    @Test
    void shouldAcceptValidEmail() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": "test@example.com"
                                }
                                """)
                )
                .andExpect(status().isAccepted());

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // ========================================
    // 2. 존재하지 않는 이메일도 동일한 응답
    // ========================================

    @Test
    void shouldAcceptUnknownEmail() throws Exception {

        // Service는 존재하지 않는 사용자를
        // 조용히 처리하도록 구현되어 있음
        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": "unknown@example.com"
                                }
                                """)
                )
                .andExpect(status().isAccepted());

        verify(temporaryPasswordService)
                .issueTemporaryPassword("unknown@example.com");
    }

    // ========================================
    // 3. 이메일 형식이 잘못되면 400
    // ========================================

    @Test
    void shouldRejectInvalidEmail() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": "invalid-email"
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(temporaryPasswordService);
    }

    // ========================================
    // 4. 이메일이 빈 문자열이면 400
    // ========================================

    @Test
    void shouldRejectBlankEmail() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": ""
                                }
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(temporaryPasswordService);
    }

    // ========================================
    // 5. 이메일 필드가 없으면 400
    // ========================================

    @Test
    void shouldRejectMissingEmail() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {}
                                """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(temporaryPasswordService);
    }

    // ========================================
    // 6. 인증 없이 발급 요청 가능
    // ========================================

    @Test
    void shouldAllowUnauthenticatedRequest() throws Exception {

        mockMvc.perform(
                        post(URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "email": "guest@example.com"
                                }
                                """)
                )
                .andExpect(status().isAccepted());

        verify(temporaryPasswordService)
                .issueTemporaryPassword("guest@example.com");

        // 인증 없는 요청이므로 JWT Decoder는 호출되지 않음
        verifyNoInteractions(jwtDecoder);
    }
}
