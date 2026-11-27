package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.ratelimit.TemporaryPasswordRateLimiter;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        CsrfController.class,
        TemporaryPasswordController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class CsrfSecurityTest {

    private static final String CSRF_URL =
            "/api/auth/csrf-token";

    private static final String REQUEST_URL =
            "/api/auth/temporary-password";

    private static final String COOKIE_NAME =
            "XSRF-TOKEN";

    private static final String HEADER_NAME =
            "X-XSRF-TOKEN";

    private static final String EMAIL =
            "test@example.com";

    private static final String EMAIL_REQUEST =
            "{\"email\":\"test@example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TemporaryPasswordService temporaryPasswordService;

    @MockitoBean
    private TemporaryPasswordRateLimiter rateLimiter;

    @Test
    @DisplayName("로그인 없이 CSRF 토큰을 발급받을 수 있다")
    void shouldIssueCsrfTokenWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get(CSRF_URL))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_NAME));
    }

    @Test
    @DisplayName("프론트엔드에서 읽을 수 있는 CSRF 쿠키를 발급한다")
    void shouldIssueReadableCsrfCookie()
            throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        assertFalse(csrfCookie.isHttpOnly());
        assertFalse(csrfCookie.getValue().isBlank());
    }

    @Test
    @DisplayName("정상 CSRF 쿠키와 헤더를 전달하면 요청을 허용한다")
    void shouldAllowRequestWithValidCsrfToken()
            throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(
                post(REQUEST_URL)
                        .cookie(csrfCookie)
                        .header(
                                HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isAccepted());

        verify(temporaryPasswordService)
                .issueTemporaryPassword(EMAIL);
    }

    @Test
    @DisplayName("CSRF 토큰이 없는 요청은 403을 반환한다")
    void shouldRejectRequestWithoutCsrfToken()
            throws Exception {

        mockMvc.perform(
                post(REQUEST_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(
                rateLimiter,
                temporaryPasswordService
        );
    }

    @Test
    @DisplayName("잘못된 CSRF 토큰을 전달하면 403을 반환한다")
    void shouldRejectRequestWithInvalidCsrfToken()
            throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(
                post(REQUEST_URL)
                        .cookie(csrfCookie)
                        .header(
                                HEADER_NAME,
                                "invalid-csrf-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(
                rateLimiter,
                temporaryPasswordService
        );
    }

    @Test
    @DisplayName("CSRF 쿠키만 전달하고 헤더가 없으면 403을 반환한다")
    void shouldRejectRequestWithoutCsrfHeader()
            throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(
                post(REQUEST_URL)
                        .cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMAIL_REQUEST)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(
                rateLimiter,
                temporaryPasswordService
        );
    }

    @Test
    @DisplayName("CSRF 토큰이 JWT 인증을 대신할 수 없다")
    void shouldNotBypassAuthenticationWithCsrfToken()
            throws Exception {

        Cookie csrfCookie = issueCsrfCookie();

        mockMvc.perform(
                patch("/api/auth/password")
                        .cookie(csrfCookie)
                        .header(
                                HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"newPassword\":\"NewPassword123!\"}"
                        )
        ).andExpect(status().isUnauthorized());
    }

    private Cookie issueCsrfCookie() throws Exception {

        MvcResult result = mockMvc.perform(
                get(CSRF_URL)
        )
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_NAME))
                .andReturn();

        Cookie csrfCookie = result.getResponse()
                .getCookie(COOKIE_NAME);

        assertNotNull(csrfCookie);

        return csrfCookie;
    }
}