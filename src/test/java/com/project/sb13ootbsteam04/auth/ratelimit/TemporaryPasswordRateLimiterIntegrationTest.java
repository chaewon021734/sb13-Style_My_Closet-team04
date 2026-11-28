package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.issueCsrfCookie;
import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.withCsrf;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {
                TemporaryPasswordController.class,
                CsrfController.class
        }
)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        TemporaryPasswordRateLimiter.class
})
@DirtiesContext(
        classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD
)
class TemporaryPasswordRateLimiterIntegrationTest {

    private static final String URL =
            "/api/auth/temporary-password";

    private static final String EMAIL =
            "test@example.com";

    private static final String CLIENT_IP =
            "192.0.2.10";

    private static final String ERROR_MESSAGE =
            "임시 비밀번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TemporaryPasswordService temporaryPasswordService;

    private Cookie csrfCookie;

    // 각 테스트 실행 전에 실제 CSRF 토큰 발급
    @BeforeEach
    void setUp() throws Exception {

        csrfCookie = issueCsrfCookie(mockMvc);
    }

    @Test
    @DisplayName("동일 이메일의 3회 요청은 202, 4번째 요청은 429를 반환한다")
    void shouldReturn429AfterThreeEmailRequests()
            throws Exception {

        for (int i = 0; i < 3; i++) {

            requestTemporaryPassword(EMAIL, CLIENT_IP)
                    .andExpect(status().isAccepted());
        }

        requestTemporaryPassword(EMAIL, CLIENT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.exceptionName")
                        .value("TemporaryPasswordRateLimitException"))
                .andExpect(jsonPath("$.message")
                        .value(ERROR_MESSAGE));

        verify(temporaryPasswordService, times(3))
                .issueTemporaryPassword(EMAIL);

        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("동일 IP의 10회 요청은 202, 11번째 요청은 429를 반환한다")
    void shouldReturn429AfterTenIpRequests()
            throws Exception {

        for (int i = 0; i < 10; i++) {

            requestTemporaryPassword(
                    "user" + i + "@example.com",
                    CLIENT_IP
            ).andExpect(status().isAccepted());
        }

        requestTemporaryPassword(
                "blocked@example.com",
                CLIENT_IP
        ).andExpect(status().isTooManyRequests());

        verify(temporaryPasswordService, times(10))
                .issueTemporaryPassword(anyString());

        verify(temporaryPasswordService, never())
                .issueTemporaryPassword("blocked@example.com");
    }

    @Test
    @DisplayName("이메일 대소문자가 달라도 동일 이메일로 요청을 제한한다")
    void shouldApplySameLimitToNormalizedEmail()
            throws Exception {

        requestTemporaryPassword(
                "TEST@example.com",
                CLIENT_IP
        ).andExpect(status().isAccepted());

        requestTemporaryPassword(
                "test@example.com",
                CLIENT_IP
        ).andExpect(status().isAccepted());

        requestTemporaryPassword(
                "Test@example.com",
                CLIENT_IP
        ).andExpect(status().isAccepted());

        requestTemporaryPassword(
                "test@EXAMPLE.COM",
                CLIENT_IP
        ).andExpect(status().isTooManyRequests());

        verify(temporaryPasswordService, times(3))
                .issueTemporaryPassword(anyString());

        verify(temporaryPasswordService, never())
                .issueTemporaryPassword("test@EXAMPLE.COM");
    }

    @Test
    @DisplayName("IP를 변경하더라도 동일 이메일의 요청 제한은 유지된다")
    void shouldMaintainEmailLimitAcrossDifferentIps()
            throws Exception {

        for (int i = 1; i <= 3; i++) {

            requestTemporaryPassword(
                    EMAIL,
                    "192.0.2." + i
            ).andExpect(status().isAccepted());
        }

        requestTemporaryPassword(
                EMAIL,
                "192.0.2.4"
        ).andExpect(status().isTooManyRequests());

        verify(temporaryPasswordService, times(3))
                .issueTemporaryPassword(EMAIL);

        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("이메일과 IP가 다르면 독립적으로 요청을 허용한다")
    void shouldAllowIndependentEmailAndIpRequests()
            throws Exception {

        for (int i = 0; i < 3; i++) {

            requestTemporaryPassword(
                    EMAIL,
                    CLIENT_IP
            ).andExpect(status().isAccepted());
        }

        requestTemporaryPassword(
                EMAIL,
                CLIENT_IP
        ).andExpect(status().isTooManyRequests());

        requestTemporaryPassword(
                "another@example.com",
                "192.0.2.20"
        ).andExpect(status().isAccepted());

        verify(temporaryPasswordService, times(3))
                .issueTemporaryPassword(EMAIL);

        verify(temporaryPasswordService, times(1))
                .issueTemporaryPassword("another@example.com");

        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("잘못된 이메일 요청은 정상 요청 횟수에 포함하지 않는다")
    void shouldNotCountInvalidEmailRequests()
            throws Exception {

        for (int i = 0; i < 10; i++) {

            requestTemporaryPassword(
                    "invalid-email",
                    CLIENT_IP
            ).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(temporaryPasswordService);

        requestTemporaryPassword(
                EMAIL,
                CLIENT_IP
        ).andExpect(status().isAccepted());

        verify(temporaryPasswordService, times(1))
                .issueTemporaryPassword(EMAIL);
    }

    // 실제 CSRF 쿠키 및 헤더를 포함하여 임시 비밀번호 요청
    private ResultActions requestTemporaryPassword(
            String email,
            String ipAddress
    ) throws Exception {

        return mockMvc.perform(
                withCsrf(post(URL), csrfCookie)
                        .with(request -> {
                            request.setRemoteAddr(ipAddress);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"email\":\"" + email + "\"}"
                        )
        );
    }
}