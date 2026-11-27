package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenCookieProvider;
import com.project.sb13ootbsteam04.auth.service.RefreshTokenService;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = {
                AuthController.class,
                CsrfController.class
        },
        properties = {
                "jwt.refresh-token-cookie-secure=true"
        }
)
@Import({
        SecurityConfig.class,
        RefreshTokenCookieProvider.class
})
class AuthControllerTest {

    private static final String CSRF_URL =
            "/api/auth/csrf-token";

    private static final String CSRF_COOKIE_NAME =
            "XSRF-TOKEN";

    private static final String CSRF_HEADER_NAME =
            "X-XSRF-TOKEN";

    private static final String LOGIN_URL =
            "/api/auth/login";

    private static final String SIGN_IN_URL =
            "/api/auth/sign-in";

    private static final String REFRESH_URL =
            "/api/auth/refresh";

    private static final String SIGN_OUT_URL =
            "/api/auth/sign-out";

    private static final String REFRESH_COOKIE_NAME =
            RefreshTokenCookieProvider.COOKIE_NAME;

    private static final String RAW_REFRESH_TOKEN =
            "a".repeat(43);

    private static final String NEW_REFRESH_TOKEN =
            "b".repeat(43);

    private static final UUID TEST_USER_ID =
            UUID.fromString(
                    "00000000-0000-0000-0000-000000000001"
            );

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
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Cookie csrfCookie;

    private LoginResult loginResult;

    private LoginResult refreshedLoginResult;

    @BeforeEach
    void setUp() throws Exception {

        // 실제 CSRF 토큰 발급
        MvcResult result = mockMvc.perform(get(CSRF_URL))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(CSRF_COOKIE_NAME))
                .andReturn();

        csrfCookie = result.getResponse()
                .getCookie(CSRF_COOKIE_NAME);

        assertNotNull(csrfCookie);

        // 일반 로그인 성공 응답
        loginResult = new LoginResult(
                new LoginResponse(
                        "test.jwt.token",
                        "Bearer",
                        TEST_USER_DTO
                ),
                new RefreshTokenService.IssuedRefreshToken(
                        TEST_USER_ID,
                        RAW_REFRESH_TOKEN,
                        Instant.now().plusSeconds(604800)
                )
        );

        // Refresh Token 갱신 성공 응답
        refreshedLoginResult = new LoginResult(
                new LoginResponse(
                        "new.jwt.token",
                        "Bearer",
                        TEST_USER_DTO
                ),
                new RefreshTokenService.IssuedRefreshToken(
                        TEST_USER_ID,
                        NEW_REFRESH_TOKEN,
                        Instant.now().plusSeconds(3600)
                )
        );

        clearInvocations(jwtDecoder);
    }

    // ========================================
    // 기존 JSON 로그인 API 테스트
    // ========================================

    // 1. 정상 로그인 → 200 OK
    @Test
    void shouldReturn200WhenLoginSucceeds() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(loginResult);

        mockMvc.perform(post(LOGIN_URL)
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
                        .doesNotExist())
                .andExpect(jsonPath("$.refreshToken")
                        .doesNotExist())
                .andExpect(content().string(
                        not(containsString(RAW_REFRESH_TOKEN))
                ))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        RAW_REFRESH_TOKEN
                ))
                .andExpect(cookie().httpOnly(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().secure(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        "/api/auth"
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));
    }

    // 2. 존재하지 않는 이메일 → 401
    @Test
    void shouldReturn401WhenEmailDoesNotExist() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(post(LOGIN_URL)
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
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));
    }

    // 3. 비밀번호 오류 → 401
    @Test
    void shouldReturn401WhenPasswordIsWrong() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException(
                        "이메일 또는 비밀번호가 올바르지 않습니다."
                ));

        mockMvc.perform(post(LOGIN_URL)
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
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));
    }

    // 4. 잠긴 계정 → 401
    @Test
    void shouldReturn401WhenAccountIsLocked() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new LockedException("잠긴 계정입니다."));

        mockMvc.perform(post(LOGIN_URL)
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
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));
    }

    // 5. 이메일 형식 오류 → 400
    @Test
    void shouldReturn400WhenEmailIsInvalid() throws Exception {

        mockMvc.perform(post(LOGIN_URL)
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

        verifyNoInteractions(
                authService,
                refreshTokenService
        );
    }

    // ========================================
    // 프론트엔드 FormData 로그인 API 테스트
    // ========================================

    // 6. 정상 FormData 로그인 → 200
    @Test
    void shouldReturn200WhenFormDataLoginSucceeds() throws Exception {

        LoginRequest expectedRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );

        when(authService.login(expectedRequest))
                .thenReturn(loginResult);

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
                        .doesNotExist())
                .andExpect(jsonPath("$.refreshToken")
                        .doesNotExist())
                .andExpect(content().string(
                        not(containsString(RAW_REFRESH_TOKEN))
                ))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        RAW_REFRESH_TOKEN
                ))
                .andExpect(cookie().httpOnly(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().secure(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        "/api/auth"
                ));

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
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));

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
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));

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

        verifyNoInteractions(
                authService,
                refreshTokenService
        );
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

        verifyNoInteractions(
                authService,
                refreshTokenService
        );
    }

    // 11. CSRF 토큰 없이 FormData 로그인 → 403
    @Test
    void shouldReturn403WhenFormDataCsrfTokenIsMissing() throws Exception {

        mockMvc.perform(multipart(SIGN_IN_URL)
                        .param("username", "test@example.com")
                        .param("password", "password123"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(
                authService,
                refreshTokenService
        );
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

        verifyNoInteractions(
                authService,
                refreshTokenService
        );
    }

    // ========================================
    // 임시 비밀번호 로그인 테스트
    // ========================================

    // 13. 임시 비밀번호 로그인 시 Refresh Token 쿠키 제거
    @Test
    void shouldClearRefreshCookieWhenTemporaryLoginSucceeds()
            throws Exception {

        LoginResult temporaryLoginResult = new LoginResult(
                new LoginResponse(
                        "temporary.jwt.token",
                        "Bearer",
                        TEST_USER_DTO
                ),
                null
        );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(temporaryLoginResult);

        mockMvc.perform(post(LOGIN_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "temporary-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("temporary.jwt.token"))
                .andExpect(jsonPath("$.refreshToken")
                        .doesNotExist())
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        ""
                ))
                .andExpect(cookie().maxAge(
                        REFRESH_COOKIE_NAME,
                        0
                ))
                .andExpect(cookie().httpOnly(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().secure(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        "/api/auth"
                ));
    }

    // ========================================
    // Refresh Token 갱신 API 테스트
    // ========================================

    // 14. 정상 Refresh Token 갱신 → 200
    @Test
    void shouldReturn200WhenRefreshSucceeds() throws Exception {

        when(refreshTokenService.rotate(RAW_REFRESH_TOKEN))
                .thenReturn(refreshedLoginResult);

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new.jwt.token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"))
                .andExpect(jsonPath("$.userDto.id")
                        .value(TEST_USER_ID.toString()))
                .andExpect(jsonPath("$.userDto.role")
                        .value("USER"))
                .andExpect(jsonPath("$.refreshToken")
                        .doesNotExist())
                .andExpect(content().string(
                        not(containsString(NEW_REFRESH_TOKEN))
                ))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        NEW_REFRESH_TOKEN
                ))
                .andExpect(cookie().httpOnly(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().secure(
                        REFRESH_COOKIE_NAME,
                        true
                ))
                .andExpect(cookie().path(
                        REFRESH_COOKIE_NAME,
                        "/api/auth"
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        "no-store"
                ));

        verify(refreshTokenService).rotate(RAW_REFRESH_TOKEN);
        verifyNoInteractions(jwtDecoder);
    }

    // 15. 유효하지 않은 Access Token 헤더가 있어도 갱신 성공
    @Test
    void shouldRefreshEvenWhenInvalidAccessTokenIsPresent()
            throws Exception {

        when(refreshTokenService.rotate(RAW_REFRESH_TOKEN))
                .thenReturn(refreshedLoginResult);

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer expired.jwt.token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("new.jwt.token"))
                .andExpect(cookie().value(
                        REFRESH_COOKIE_NAME,
                        NEW_REFRESH_TOKEN
                ));

        verify(refreshTokenService).rotate(RAW_REFRESH_TOKEN);

        // 갱신 전용 SecurityFilterChain에서는 JWT 검증을 하지 않음
        verifyNoInteractions(jwtDecoder);
    }

    // 16. Refresh Token 쿠키 누락 → 401
    @Test
    void shouldReturn401WhenRefreshCookieIsMissing()
            throws Exception {

        when(refreshTokenService.rotate(isNull()))
                .thenThrow(new BadCredentialsException(
                        "유효하지 않은 Refresh Token입니다."
                ));

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(csrfCookie)
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));

        verify(refreshTokenService).rotate(isNull());
    }

    // 17. 유효하지 않은 Refresh Token → 401
    @Test
    void shouldReturn401WhenRefreshTokenIsInvalid()
            throws Exception {

        when(refreshTokenService.rotate(RAW_REFRESH_TOKEN))
                .thenThrow(new BadCredentialsException(
                        "유효하지 않은 Refresh Token입니다."
                ));

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                csrfCookie.getValue()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist(
                        REFRESH_COOKIE_NAME
                ));

        verify(refreshTokenService).rotate(RAW_REFRESH_TOKEN);
    }

    // 18. CSRF 토큰 없이 갱신 요청 → 403
    @Test
    void shouldReturn403WhenRefreshCsrfTokenIsMissing()
            throws Exception {

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie(
                                REFRESH_COOKIE_NAME,
                                RAW_REFRESH_TOKEN
                        )))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    // 19. 잘못된 CSRF 토큰으로 갱신 요청 → 403
    @Test
    void shouldReturn403WhenRefreshCsrfTokenIsInvalid()
            throws Exception {

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        )
                        .header(
                                CSRF_HEADER_NAME,
                                "invalid-csrf-token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    // 20. Bearer 헤더가 있어도 CSRF 검증은 생략되지 않음
    @Test
    void shouldReturn403WhenBearerTokenIsPresentWithoutCsrf()
            throws Exception {

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(new Cookie(
                                REFRESH_COOKIE_NAME,
                                RAW_REFRESH_TOKEN
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer expired.jwt.token"
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(
                refreshTokenService,
                jwtDecoder
        );
    }

    // 21. CSRF 쿠키만 전달하고 헤더가 누락된 경우 → 403
    @Test
    void shouldReturn403WhenRefreshCsrfHeaderIsMissing()
            throws Exception {

        mockMvc.perform(post(REFRESH_URL)
                        .cookie(
                                csrfCookie,
                                new Cookie(
                                        REFRESH_COOKIE_NAME,
                                        RAW_REFRESH_TOKEN
                                )
                        ))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService);
    }

    // ========================================
    // 로그아웃 API 테스트
    // ========================================

    // 22. 정상 로그아웃 요청 → 204
    @Test
    void shouldReturn204WhenSignOutSucceeds() throws Exception {
        mockMvc.perform(signOutRequest())
                .andExpect(status().isNoContent());

        verify(refreshTokenService).logout(RAW_REFRESH_TOKEN);
        verifyNoInteractions(authService, jwtDecoder);
    }

    // 23. Refresh Token 쿠키 삭제
    @Test
    void shouldClearRefreshCookieOnSignOut() throws Exception {
        mockMvc.perform(signOutRequest())
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(REFRESH_COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0))
                .andExpect(cookie().path(REFRESH_COOKIE_NAME, "/api/auth"));

        verify(refreshTokenService).logout(RAW_REFRESH_TOKEN);
    }

    // 24. 쿠키 삭제 시 보안 속성 유지
    @Test
    void shouldPreserveCookieSecurityAttributesOnSignOut() throws Exception {
        mockMvc.perform(signOutRequest())
                .andExpect(status().isNoContent())
                .andExpect(cookie().httpOnly(REFRESH_COOKIE_NAME, true))
                .andExpect(cookie().secure(REFRESH_COOKIE_NAME, true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")));
    }

    // 25. Refresh Token 쿠키 누락 → 204
    @Test
    void shouldReturn204WhenSignOutCookieIsMissing() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .cookie(csrfCookie)
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verify(refreshTokenService).logout(isNull());
    }

    // 26. 잘못된 Refresh Token 전달 → 204
    @Test
    void shouldReturn204WhenSignOutTokenIsMalformed() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .cookie(csrfCookie,
                                new Cookie(REFRESH_COOKIE_NAME, "invalid-token"))
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verify(refreshTokenService).logout("invalid-token");
    }

    // 27. 유효하지 않은 Bearer Token이 있어도 로그아웃 허용
    @Test
    void shouldSignOutEvenWhenInvalidAccessTokenIsPresent() throws Exception {
        mockMvc.perform(signOutRequest()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.jwt.token"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE_NAME, 0));

        verify(refreshTokenService).logout(RAW_REFRESH_TOKEN);
        verifyNoInteractions(jwtDecoder);
    }

    // 28. CSRF 헤더 누락 → 403
    @Test
    void shouldReturn403WhenSignOutCsrfHeaderIsMissing() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .cookie(csrfCookie,
                                new Cookie(REFRESH_COOKIE_NAME, RAW_REFRESH_TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist(REFRESH_COOKIE_NAME));

        verifyNoInteractions(refreshTokenService, jwtDecoder);
    }

    // 29. 잘못된 CSRF 토큰 → 403
    @Test
    void shouldReturn403WhenSignOutCsrfTokenIsInvalid() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .cookie(csrfCookie,
                                new Cookie(REFRESH_COOKIE_NAME, RAW_REFRESH_TOKEN))
                        .header(CSRF_HEADER_NAME, "invalid-csrf-token"))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist(REFRESH_COOKIE_NAME));

        verifyNoInteractions(refreshTokenService, jwtDecoder);
    }

    // 30. Bearer 헤더를 통한 CSRF 우회 차단
    @Test
    void shouldReturn403WhenSignOutBearerIsPresentWithoutCsrf() throws Exception {
        mockMvc.perform(post(SIGN_OUT_URL)
                        .cookie(new Cookie(REFRESH_COOKIE_NAME, RAW_REFRESH_TOKEN))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.jwt.token"))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist(REFRESH_COOKIE_NAME));

        verifyNoInteractions(refreshTokenService, jwtDecoder);
    }

    // 31. GET 방식 로그아웃 요청 차단
    @Test
    void shouldReturn403WhenSignOutUsesGet() throws Exception {
        mockMvc.perform(get(SIGN_OUT_URL)
                        .cookie(csrfCookie,
                                new Cookie(REFRESH_COOKIE_NAME, RAW_REFRESH_TOKEN))
                        .header(CSRF_HEADER_NAME, csrfCookie.getValue()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(refreshTokenService, jwtDecoder);
    }

    // 32. 로그아웃 응답 캐시 방지
    @Test
    void shouldPreventCachingOfSignOutResponse() throws Exception {
        mockMvc.perform(signOutRequest())
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    // 33. 로그아웃 응답 Body는 비어 있어야 함
    @Test
    void shouldReturnEmptyBodyOnSignOut() throws Exception {
        mockMvc.perform(signOutRequest())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private MockHttpServletRequestBuilder signOutRequest() {
        return post(SIGN_OUT_URL)
                .cookie(csrfCookie,
                        new Cookie(REFRESH_COOKIE_NAME, RAW_REFRESH_TOKEN))
                .header(CSRF_HEADER_NAME, csrfCookie.getValue());
    }
}
