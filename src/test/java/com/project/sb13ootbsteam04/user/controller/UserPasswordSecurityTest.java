package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.issueCsrfCookie;
import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.withCsrf;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        UserPasswordController.class,
        CsrfController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class UserPasswordSecurityTest {

    private static final String PASSWORD = "NewPassword123!";
    private static final String BODY = "{\"password\":\"" + PASSWORD + "\"}";
    private static final String TOKEN = "test-token";
    private static final long VERSION = 5L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PasswordResetService passwordResetService;

    private UUID userId;
    private Cookie csrfCookie;

    @BeforeEach
    void setUp() throws Exception {

        userId = UUID.randomUUID();
        csrfCookie = issueCsrfCookie(mockMvc);
    }

    // 1. 임시 JWT로 본인 비밀번호 변경 허용
    @Test
    void shouldAllowTemporaryJwtForOwnPassword() throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isNoContent());

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);
    }

    // 2. 다른 사용자의 비밀번호 변경 차단
    @Test
    void shouldRejectDifferentUserId() throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(
                authenticated(UUID.randomUUID().toString(), BODY)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // 3. 일반 사용자 JWT 차단
    @Test
    void shouldRejectNormalUser() throws Exception {

        givenToken("NORMAL", "USER", VERSION);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // 4. 일반 관리자 JWT 차단
    @Test
    void shouldRejectNormalAdmin() throws Exception {

        givenToken("NORMAL", "ADMIN", VERSION);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(passwordResetService);
    }

    // 5. 임시 관리자 JWT로 본인 비밀번호 변경 허용
    @Test
    void shouldAllowTemporaryAdminForOwnPassword() throws Exception {

        givenToken("TEMPORARY", "ADMIN", VERSION);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isNoContent());

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);
    }

    // 6. 비인증 사용자 접근 차단
    @Test
    void shouldRejectAnonymous() throws Exception {

        mockMvc.perform(
                withCsrf(
                        request(userId.toString(), BODY),
                        csrfCookie
                )
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
    }

    // 7. 유효하지 않은 JWT 차단
    @Test
    void shouldRejectInvalidJwt() throws Exception {

        when(jwtDecoder.decode(TOKEN))
                .thenThrow(new BadJwtException("invalid token"));

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(passwordResetService);
    }

    // 8. 유효한 Bearer JWT 인증 요청은 CSRF 토큰 없이도 허용
    // Spring Security Resource Server의 기본 CSRF 제외 동작 검증
    @Test
    void shouldAllowValidBearerJwtWithoutCsrf() throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(
                request(userId.toString(), BODY)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
        ).andExpect(status().isNoContent());

        verify(jwtDecoder).decode(TOKEN);

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);

        verifyNoMoreInteractions(passwordResetService);
    }

    // 9. 유효한 Bearer JWT 인증은 CSRF 토큰 값에 의존하지 않음
    // Authorization 헤더 기반 인증 요청의 기본 동작 검증
    @Test
    void shouldAllowValidBearerJwtWithInvalidCsrf() throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(
                request(userId.toString(), BODY)
                        .cookie(csrfCookie)
                        .header(
                                "X-XSRF-TOKEN",
                                "invalid-csrf-token"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + TOKEN
                        )
        ).andExpect(status().isNoContent());

        verify(jwtDecoder).decode(TOKEN);

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);

        verifyNoMoreInteractions(passwordResetService);
    }

    // 10. 6자 미만 비밀번호 차단
    @Test
    void shouldRejectShortPassword() throws Exception {

        assertBadPassword("{\"password\":\"12345\"}");
    }

    // 11. 공백 비밀번호 차단
    @Test
    void shouldRejectBlankPassword() throws Exception {

        assertBadPassword("{\"password\":\"        \"}");
    }

    // 12. 비밀번호 필드 누락 차단
    @Test
    void shouldRejectMissingPassword() throws Exception {

        assertBadPassword("{}");
    }

    // 13. 잘못된 사용자 UUID 차단
    @Test
    void shouldRejectInvalidUuid() throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(authenticated("invalid-uuid", BODY))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    // 14. 정확한 loginVersion 전달 검증
    @Test
    void shouldPassExactLoginVersion() throws Exception {

        givenToken("TEMPORARY", "USER", 99L);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isNoContent());

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, 99L);

        verifyNoMoreInteractions(passwordResetService);
    }

    // 15. Service에서 이전 로그인 버전 차단
    @Test
    void shouldRejectStaleVersionReportedByService()
            throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        doThrow(new BadCredentialsException(
                "비밀번호를 재설정할 수 없습니다."
        ))
                .when(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);

        mockMvc.perform(authenticated(userId.toString(), BODY))
                .andExpect(status().isUnauthorized());

        verify(passwordResetService)
                .resetPassword(userId, PASSWORD, VERSION);
    }

    // 잘못된 비밀번호 요청 검증
    private void assertBadPassword(String body) throws Exception {

        givenToken("TEMPORARY", "USER", VERSION);

        mockMvc.perform(authenticated(userId.toString(), body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(passwordResetService);
    }

    // 기본 비밀번호 변경 HTTP 요청 생성
    private MockHttpServletRequestBuilder request(
            String id,
            String body
    ) {

        return patch("/api/users/" + id + "/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    // 정상 CSRF 토큰 및 JWT 인증 헤더 포함
    private MockHttpServletRequestBuilder authenticated(
            String id,
            String body
    ) {

        return withCsrf(
                request(id, body),
                csrfCookie
        ).header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + TOKEN
        );
    }

    // 테스트용 JWT 생성 및 JwtDecoder Mock 설정
    private void givenToken(
            String authType,
            String role,
            long version
    ) {

        Instant now = Instant.now();

        when(jwtDecoder.decode(TOKEN))
                .thenReturn(
                        Jwt.withTokenValue(TOKEN)
                                .header("alg", "HS256")
                                .subject(userId.toString())
                                .issuedAt(now)
                                .expiresAt(now.plusSeconds(180))
                                .claim("authType", authType)
                                .claim("role", role)
                                .claim("loginVersion", version)
                                .build()
                );
    }
}