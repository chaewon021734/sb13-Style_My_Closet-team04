package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.auth.controller.PasswordResetController;
import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        PasswordResetController.class,
        TemporaryPasswordController.class,
        TemporaryPasswordSecurityTest.TestController.class
})
@Import({
        SecurityConfig.class,
        TemporaryPasswordSecurityTest.TestController.class
})
class TemporaryPasswordSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private TemporaryPasswordService temporaryPasswordService;

    private static final String PASSWORD_REQUEST =
            "{\"newPassword\":\"NewPassword123!\"}";

    private static final String EMAIL_REQUEST =
            "{\"email\":\"test@example.com\"}";

    // 1. 임시 JWT로 비밀번호 재설정 가능
    @Test
    void shouldAllowTemporaryJwtForPasswordReset() throws Exception {

        UUID userId = UUID.randomUUID();

        when(jwtDecoder.decode("temporary-token"))
                .thenReturn(createToken(
                        "temporary-token",
                        userId,
                        "USER",
                        "TEMPORARY"
                ));

        mockMvc.perform(
                patch("/api/auth/password")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isNoContent());

        verify(passwordResetService).resetPassword(
                userId,
                "NewPassword123!"
        );
    }

    // 2. 일반 JWT로 비밀번호 재설정 차단
    @Test
    void shouldRejectNormalJwtForPasswordReset() throws Exception {

        when(jwtDecoder.decode("normal-token"))
                .thenReturn(createToken(
                        "normal-token",
                        UUID.randomUUID(),
                        "USER",
                        "NORMAL"
                ));

        mockMvc.perform(
                patch("/api/auth/password")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer normal-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // 3. 임시 JWT로 일반 API 접근 차단
    @Test
    void shouldRejectTemporaryJwtForGeneralApi() throws Exception {

        when(jwtDecoder.decode("temporary-token"))
                .thenReturn(createToken(
                        "temporary-token",
                        UUID.randomUUID(),
                        "USER",
                        "TEMPORARY"
                ));

        mockMvc.perform(
                get("/api/security-test/secured")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-token"
                        )
        ).andExpect(status().isForbidden());
    }

    // 4. 임시 JWT로 관리자 API 접근 차단
    @Test
    void shouldRejectTemporaryJwtForAdminApi() throws Exception {

        when(jwtDecoder.decode("temporary-admin-token"))
                .thenReturn(createToken(
                        "temporary-admin-token",
                        UUID.randomUUID(),
                        "ADMIN",
                        "TEMPORARY"
                ));

        mockMvc.perform(
                get("/api/admin/security-test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-admin-token"
                        )
        ).andExpect(status().isForbidden());
    }

    // 5. JWT 없이 비밀번호 재설정 차단
    @Test
    void shouldRejectAnonymousPasswordReset() throws Exception {

        mockMvc.perform(
                patch("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
    }

    // 6. 인증 없이 임시 비밀번호 발급 API 접근 가능
    @Test
    void shouldAllowAnonymousTemporaryPasswordRequest() throws Exception {

        mockMvc.perform(
                post("/api/auth/temporary-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isAccepted());

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // 7. 이메일 형식이 올바르지 않으면 400
    @Test
    void shouldRejectInvalidEmail() throws Exception {

        mockMvc.perform(
                post("/api/auth/temporary-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\"}")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(temporaryPasswordService);
    }

    // 8. 새 비밀번호가 8자 미만이면 400
    @Test
    void shouldRejectShortNewPassword() throws Exception {

        when(jwtDecoder.decode("temporary-token"))
                .thenReturn(createToken(
                        "temporary-token",
                        UUID.randomUUID(),
                        "USER",
                        "TEMPORARY"
                ));

        mockMvc.perform(
                patch("/api/auth/password")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123\"}")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    private Jwt createToken(
            String tokenValue,
            UUID userId,
            String role,
            String authType
    ) {

        Instant now = Instant.now();

        return Jwt.withTokenValue(tokenValue)
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(180))
                .claim("role", role)
                .claim("authType", authType)
                .claim("loginVersion", 1L)
                .build();
    }

    @RestController
    static class TestController {

        @GetMapping("/api/security-test/secured")
        public ResponseEntity<String> secured() {
            return ResponseEntity.ok("인증 성공");
        }

        @GetMapping("/api/admin/security-test")
        public ResponseEntity<String> adminSecured() {
            return ResponseEntity.ok("관리자 인증 성공");
        }
    }
}
