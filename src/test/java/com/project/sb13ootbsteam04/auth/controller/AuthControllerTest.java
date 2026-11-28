package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        AuthController.class,
        CsrfController.class
})
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final String CSRF_URL =
            "/api/auth/csrf-token";

    private static final String CSRF_COOKIE_NAME =
            "XSRF-TOKEN";

    private static final String CSRF_HEADER_NAME =
            "X-XSRF-TOKEN";

    private static final String SIGN_IN_URL =
            "/api/auth/sign-in";

    // 테스트용 사용자 정보
    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UserDto TEST_USER_DTO =
            new UserDto(
                    TEST_USER_ID,
                    Instant.parse("2026-01-01T00:00:00Z"),
                    "test@example.com",
                    "테스트 사용자",
                    "USER",
                    false
            );

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Cookie csrfCookie;

    // 각 테스트 실행 전에 실제 CSRF 토큰 발급
    @BeforeEach
    void setUp() throws Exception {

        MvcResult result = mockMvc.perform(get(CSRF_URL))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(CSRF_COOKIE_NAME))
                .andReturn();

        csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);

        assertNotNull(csrfCookie);
    }

    // ========================================
    // 기존 JSON 로그인 API 테스트
    // ========================================

    // 1. 정상 로그인 → 200 OK
    @Test
    void shouldReturn200WhenLoginSucceeds() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(
                        "test.jwt.token",
                        "Bearer",
                        TEST_USER_DTO
                ));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
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
                        .value("Bearer"))
                .andExpect(jsonPath("$.userDto.id")
                        .value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.email")
                        .value("test@example.com"))
                .andExpect(jsonPath("$.userDto.name")
                        .value("테스트 사용자"))
                .andExpect(jsonPath("$.userDto.role")
                        .value("USER"))
                .andExpect(jsonPath("$.userDto.locked")
                        .value(false))
                .andExpect(jsonPath("$.userDto.password")
                        .doesNotExist());
    }

    // 2. 존재하지 않는 이메일 → 401
    @Test
    void shouldReturn401WhenEmailDoesNotExist() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
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
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
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
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
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
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // ========================================
    // 프론트엔드 FormData 로그인 API 테스트
    // ========================================

    // 6. 정상 FormData 로그인 및 사용자 정보 반환 → 200
    @Test
    void shouldReturn200WhenFormDataLoginSucceeds() throws Exception {

        LoginRequest expectedRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );

        when(authService.login(expectedRequest))
                .thenReturn(new LoginResponse(
                        "test.jwt.token",
                        "Bearer",
                        TEST_USER_DTO
                ));

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "password123")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("test.jwt.token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"))
                .andExpect(jsonPath("$.userDto.id")
                        .value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.email")
                        .value("test@example.com"))
                .andExpect(jsonPath("$.userDto.name")
                        .value("테스트 사용자"))
                .andExpect(jsonPath("$.userDto.role")
                        .value("USER"))
                .andExpect(jsonPath("$.userDto.locked")
                        .value(false))
                .andExpect(jsonPath("$.userDto.password")
                        .doesNotExist());

        verify(authService).login(expectedRequest);
    }

    // 7. FormData 로그인 비밀번호 오류 → 401
    @Test
    void shouldReturn401WhenFormDataPasswordIsWrong() throws Exception {

        LoginRequest expectedRequest = new LoginRequest(
                "test@example.com",
                "wrong-password"
        );

        when(authService.login(expectedRequest))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "wrong-password")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isUnauthorized());

        verify(authService).login(expectedRequest);
    }

    // 8. FormData 로그인 잠긴 계정 → 401
    @Test
    void shouldReturn401WhenFormDataAccountIsLocked() throws Exception {

        LoginRequest expectedRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );

        when(authService.login(expectedRequest))
                .thenThrow(new LockedException("잠긴 계정입니다."));

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "password123")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isUnauthorized());

        verify(authService).login(expectedRequest);
    }

    // 9. FormData 이메일 형식 오류 → 400
    @Test
    void shouldReturn400WhenFormDataEmailIsInvalid() throws Exception {

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "invalid-email")
                        .param("password", "password123")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // 10. FormData 비밀번호 누락 → 400
    @Test
    void shouldReturn400WhenFormDataPasswordIsMissing() throws Exception {

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    // 11. CSRF 토큰 없이 FormData 로그인 → 403
    @Test
    void shouldReturn403WhenFormDataCsrfTokenIsMissing() throws Exception {

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    // 12. 잘못된 CSRF 토큰으로 FormData 로그인 → 403
    @Test
    void shouldReturn403WhenFormDataCsrfTokenIsInvalid() throws Exception {

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "password123")
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                "invalid-csrf-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }
}