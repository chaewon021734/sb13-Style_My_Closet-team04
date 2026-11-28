package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.controller.PasswordResetController;
import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;
import com.project.sb13ootbsteam04.auth.ratelimit.TemporaryPasswordRateLimiter;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
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

import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.issueCsrfCookie;
import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.withCsrf;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        PasswordResetController.class,
        TemporaryPasswordController.class,
        CsrfController.class,
        TemporaryPasswordSecurityTest.TestController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
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

    @MockitoBean
    private TemporaryPasswordRateLimiter rateLimiter;

    private static final String PASSWORD_REQUEST =
            "{\"newPassword\":\"NewPassword123!\"}";

    private static final String EMAIL_REQUEST =
            "{\"email\":\"test@example.com\"}";

    private static final String CLIENT_IP = "192.0.2.10";

    private Cookie csrfCookie;

    // 각 테스트 실행 전에 실제 CSRF 토큰 발급
    @BeforeEach
    void setUp() throws Exception {

        csrfCookie = issueCsrfCookie(mockMvc);
    }

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
                withCsrf(
                        patch("/api/auth/password"),
                        csrfCookie
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isNoContent());

        verify(passwordResetService).resetPassword(
                userId,
                "NewPassword123!",
                1L
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
                withCsrf(
                        patch("/api/auth/password"),
                        csrfCookie
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer normal-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isForbidden());

        // CSRF 필터가 아닌 JWT 권한 검증 단계까지 진행됐는지 확인
        verify(jwtDecoder).decode("normal-token");

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
                withCsrf(
                        patch("/api/auth/password"),
                        csrfCookie
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PASSWORD_REQUEST)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
    }

    // 6. 인증 없이 임시 비밀번호 발급 API 접근 가능
    @Test
    void shouldAllowAnonymousTemporaryPasswordRequest() throws Exception {

        mockMvc.perform(
                withCsrf(
                        post("/api/auth/temporary-password"),
                        csrfCookie
                )
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isAccepted());

        verify(rateLimiter).checkAndRecord(
                "test@example.com",
                CLIENT_IP
        );

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // 7. 이메일 형식이 올바르지 않으면 400
    @Test
    void shouldRejectInvalidEmail() throws Exception {

        mockMvc.perform(
                withCsrf(
                        post("/api/auth/temporary-password"),
                        csrfCookie
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\"}")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(
                rateLimiter,
                temporaryPasswordService
        );
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
                withCsrf(
                        patch("/api/auth/password"),
                        csrfCookie
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer temporary-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123\"}")
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    // 9. 이메일 요청 제한 초과 시 HTTP 429
    @Test
    void shouldReturn429WhenEmailRateLimitExceeded() throws Exception {

        doThrow(new TemporaryPasswordRateLimitException())
                .when(rateLimiter)
                .checkAndRecord("test@example.com", CLIENT_IP);

        mockMvc.perform(
                withCsrf(
                        post("/api/auth/temporary-password"),
                        csrfCookie
                )
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isTooManyRequests())
         .andExpect(jsonPath("$.exceptionName")
                 .value("TemporaryPasswordRateLimitException"))
         .andExpect(jsonPath("$.message")
                 .value("임시 비밀번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요."));

        verifyNoInteractions(temporaryPasswordService);
    }

    // 10. IP 요청 제한 초과 시 HTTP 429
    @Test
    void shouldReturn429WhenIpRateLimitExceeded() throws Exception {

        doThrow(new TemporaryPasswordRateLimitException())
                .when(rateLimiter)
                .checkAndRecord("test@example.com", CLIENT_IP);

        mockMvc.perform(
                withCsrf(
                        post("/api/auth/temporary-password"),
                        csrfCookie
                )
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isTooManyRequests());

        verify(temporaryPasswordService, never())
                .issueTemporaryPassword(anyString());
    }

    // 11. 제한 초과 시 발급 서비스 실행 차단
    @Test
    void shouldNotIssuePasswordWhenRateLimitExceeded() throws Exception {

        doThrow(new TemporaryPasswordRateLimitException())
                .when(rateLimiter)
                .checkAndRecord("test@example.com", CLIENT_IP);

        mockMvc.perform(
                withCsrf(
                        post("/api/auth/temporary-password"),
                        csrfCookie
                )
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isTooManyRequests());

        verifyNoInteractions(temporaryPasswordService);
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
