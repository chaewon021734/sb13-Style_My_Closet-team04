package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.controller.TemporaryPasswordController;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import com.project.sb13ootbsteam04.global.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
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

@WebMvcTest(controllers = {TemporaryPasswordController.class, CsrfController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TemporaryPasswordRateLimiter.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TemporaryPasswordRateLimiterIntegrationTest {
    private static final String URL = "/api/auth/temporary-password";
    private static final String ALIAS_URL = "/api/auth/reset-password";
    private static final String EMAIL = "test@example.com";
    private static final String CLIENT_IP = "192.0.2.10";
    private static final String ERROR_MESSAGE = "임시 비밀번호 요청 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private TemporaryPasswordService temporaryPasswordService;

    // 요청 기록은 테스트용 Redis 컨테이너에 저장
    @TestConfiguration
    static class RedisConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return RedisTestSupport.template();
        }
    }

    // Docker가 없으면 컨텍스트를 띄우기 전에 건너뛴다
    @BeforeAll
    static void requireRedis() {
        RedisTestSupport.template();
    }
    private Cookie csrfCookie;

    @BeforeEach
    void setUp() throws Exception {
        csrfCookie = issueCsrfCookie(mockMvc);
    }

    @Test
    @DisplayName("동일 이메일의 3회 요청은 202, 4번째 요청은 429를 반환한다")
    void shouldReturn429AfterThreeEmailRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestTemporaryPassword(EMAIL, CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(EMAIL, CLIENT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.exceptionName").value("TemporaryPasswordRateLimitException"))
                .andExpect(jsonPath("$.message").value(ERROR_MESSAGE));
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(EMAIL);
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("동일 IP의 10회 요청은 202, 11번째 요청은 429를 반환한다")
    void shouldReturn429AfterTenIpRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestTemporaryPassword("user" + i + "@example.com", CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword("blocked@example.com", CLIENT_IP).andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(10)).issueTemporaryPassword(anyString());
        verify(temporaryPasswordService, never()).issueTemporaryPassword("blocked@example.com");
    }

    @Test
    @DisplayName("이메일 대소문자가 달라도 동일 이메일로 요청을 제한한다")
    void shouldApplySameLimitToNormalizedEmail() throws Exception {
        for (String email : new String[]{"TEST@example.com", "test@example.com", "Test@example.com"}) {
            requestTemporaryPassword(email, CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword("test@EXAMPLE.COM", CLIENT_IP).andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(anyString());
        verify(temporaryPasswordService, never()).issueTemporaryPassword("test@EXAMPLE.COM");
    }

    @Test
    @DisplayName("IP를 변경하더라도 동일 이메일의 요청 제한은 유지된다")
    void shouldMaintainEmailLimitAcrossDifferentIps() throws Exception {
        for (int i = 1; i <= 3; i++) {
            requestTemporaryPassword(EMAIL, "192.0.2." + i).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(EMAIL, "192.0.2.4").andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(EMAIL);
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("이메일과 IP가 다르면 독립적으로 요청을 허용한다")
    void shouldAllowIndependentEmailAndIpRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestTemporaryPassword(EMAIL, CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(EMAIL, CLIENT_IP).andExpect(status().isTooManyRequests());
        requestTemporaryPassword("another@example.com", "192.0.2.20").andExpect(status().isAccepted());
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(EMAIL);
        verify(temporaryPasswordService).issueTemporaryPassword("another@example.com");
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    @DisplayName("잘못된 이메일 요청은 정상 요청 횟수에 포함하지 않는다")
    void shouldNotCountInvalidEmailRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestTemporaryPassword("invalid-email", CLIENT_IP).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(temporaryPasswordService);
        requestTemporaryPassword(EMAIL, CLIENT_IP).andExpect(status().isAccepted());
        verify(temporaryPasswordService).issueTemporaryPassword(EMAIL);
    }

    // 프론트엔드 호환 API 검증
    @Test
    void shouldAllowAnonymousAliasRequest() throws Exception {
        requestTemporaryPassword(ALIAS_URL, EMAIL, CLIENT_IP)
                .andExpect(status().isAccepted()).andExpect(content().string(""));
        verify(temporaryPasswordService).issueTemporaryPassword(EMAIL);
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    void shouldRejectInvalidAliasEmail() throws Exception {
        requestTemporaryPassword(ALIAS_URL, "invalid-email", CLIENT_IP).andExpect(status().isBadRequest());
        verifyNoInteractions(temporaryPasswordService);
    }

    @Test
    void shouldRejectBlankAliasEmail() throws Exception {
        requestTemporaryPassword(ALIAS_URL, "", CLIENT_IP).andExpect(status().isBadRequest());
        verifyNoInteractions(temporaryPasswordService);
    }

    @Test
    void shouldRejectAliasWithoutCsrf() throws Exception {
        mockMvc.perform(post(ALIAS_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(temporaryPasswordService);
    }

    @Test
    void shouldRejectAliasWithInvalidCsrf() throws Exception {
        mockMvc.perform(post(ALIAS_URL).cookie(csrfCookie)
                .header("X-XSRF-TOKEN", "invalid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(temporaryPasswordService);
    }

    @Test
    void shouldLimitAliasEmailRequests() throws Exception {
        for (int i = 0; i < 3; i++) {
            requestTemporaryPassword(ALIAS_URL, EMAIL, CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(ALIAS_URL, EMAIL, CLIENT_IP)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.exceptionName").value("TemporaryPasswordRateLimitException"))
                .andExpect(jsonPath("$.message").value(ERROR_MESSAGE));
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(EMAIL);
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    void shouldLimitAliasIpRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestTemporaryPassword(ALIAS_URL, "user" + i + "@example.com", CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(ALIAS_URL, "blocked@example.com", CLIENT_IP).andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(10)).issueTemporaryPassword(anyString());
        verify(temporaryPasswordService, never()).issueTemporaryPassword("blocked@example.com");
    }

    @Test
    void shouldShareEmailLimitAcrossBothPaths() throws Exception {
        requestTemporaryPassword(URL, EMAIL, "192.0.2.1").andExpect(status().isAccepted());
        requestTemporaryPassword(ALIAS_URL, "TEST@example.com", "192.0.2.2").andExpect(status().isAccepted());
        requestTemporaryPassword(URL, EMAIL, "192.0.2.3").andExpect(status().isAccepted());
        requestTemporaryPassword(ALIAS_URL, EMAIL, "192.0.2.4").andExpect(status().isTooManyRequests());
        requestTemporaryPassword(URL, EMAIL, "192.0.2.5").andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(2)).issueTemporaryPassword(EMAIL);
        verify(temporaryPasswordService).issueTemporaryPassword("TEST@example.com");
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    @Test
    void shouldShareIpLimitAcrossBothPaths() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestTemporaryPassword(i % 2 == 0 ? URL : ALIAS_URL,
                    "user" + i + "@example.com", CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(ALIAS_URL, "blocked1@example.com", CLIENT_IP).andExpect(status().isTooManyRequests());
        requestTemporaryPassword(URL, "blocked2@example.com", CLIENT_IP).andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(10)).issueTemporaryPassword(anyString());
        verify(temporaryPasswordService, never()).issueTemporaryPassword("blocked1@example.com");
        verify(temporaryPasswordService, never()).issueTemporaryPassword("blocked2@example.com");
    }

    @Test
    void shouldNotCountInvalidAliasRequests() throws Exception {
        for (int i = 0; i < 10; i++) {
            requestTemporaryPassword(ALIAS_URL, "invalid-email", CLIENT_IP).andExpect(status().isBadRequest());
            mockMvc.perform(post(ALIAS_URL).contentType(MediaType.APPLICATION_JSON)
                    .with(request -> { request.setRemoteAddr(CLIENT_IP); return request; })
                    .content("{\"email\":\"" + EMAIL + "\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(temporaryPasswordService);
        for (int i = 0; i < 3; i++) {
            requestTemporaryPassword(ALIAS_URL, EMAIL, CLIENT_IP).andExpect(status().isAccepted());
        }
        requestTemporaryPassword(URL, EMAIL, CLIENT_IP).andExpect(status().isTooManyRequests());
        verify(temporaryPasswordService, times(3)).issueTemporaryPassword(EMAIL);
        verifyNoMoreInteractions(temporaryPasswordService);
    }

    private ResultActions requestTemporaryPassword(String email, String ipAddress) throws Exception {
        return requestTemporaryPassword(URL, email, ipAddress);
    }

    private ResultActions requestTemporaryPassword(String url, String email, String ipAddress) throws Exception {
        return mockMvc.perform(withCsrf(post(url), csrfCookie)
                .with(request -> { request.setRemoteAddr(ipAddress); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }
}
