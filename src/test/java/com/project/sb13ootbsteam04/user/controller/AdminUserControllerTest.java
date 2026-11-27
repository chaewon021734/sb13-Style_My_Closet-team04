package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.service.AdminUserService;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.issueCsrfCookie;
import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.withCsrf;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AdminUserController.class,
        CsrfController.class
})
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private Cookie csrfCookie;

    // 각 테스트 실행 전에 실제 CSRF 토큰 발급
    @BeforeEach
    void setUp() throws Exception {

        csrfCookie = issueCsrfCookie(mockMvc);
    }

    // ========================================
    // 1. ADMIN 권한으로 사용자 권한 변경
    // ========================================

    @Test
    void adminCanChangeUserRole() throws Exception {

        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/role", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .jwt(token -> token
                                                .claim("role", "ADMIN"))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        verify(adminUserService)
                .changeRole(userId, UserRole.ADMIN);
    }

    // ========================================
    // 2. ADMIN 권한으로 계정 잠금
    // ========================================

    @Test
    void adminCanLockUser() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID currentAdminId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": true
                }
                """;

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/locked", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .jwt(token -> token
                                                .subject(currentAdminId.toString())
                                                .claim("role", "ADMIN"))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        verify(adminUserService)
                .changeLocked(
                        userId,
                        true,
                        currentAdminId
                );
    }

    // ========================================
    // 3. ADMIN 권한으로 계정 잠금 해제
    // ========================================

    @Test
    void adminCanUnlockUser() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID currentAdminId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": false
                }
                """;

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/locked", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .jwt(token -> token
                                                .subject(currentAdminId.toString())
                                                .claim("role", "ADMIN"))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        verify(adminUserService)
                .changeLocked(
                        userId,
                        false,
                        currentAdminId
                );
    }

    // ========================================
    // 4. USER 권한으로 관리자 API 접근 차단
    // ========================================

    @Test
    void regularUserCannotAccessAdminApi() throws Exception {

        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/role", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .jwt(token -> token
                                                .claim("role", "USER"))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_USER"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    // ========================================
    // 5. 인증 없이 관리자 API 접근 차단
    // ========================================

    @Test
    void anonymousUserCannotAccessAdminApi() throws Exception {

        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        // 정상 CSRF 토큰이 있더라도 JWT 인증 없이는 접근 불가
        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/role", userId),
                                csrfCookie
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUserService);
    }

    // ========================================
    // 6. USER 권한으로 계정 잠금 API 접근 차단
    // ========================================

    @Test
    void regularUserCannotLockAccount() throws Exception {

        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": true
                }
                """;

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/locked", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .jwt(token -> token
                                                .claim("role", "USER"))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_USER"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    // ========================================
    // 7. role 누락 시 400 Bad Request
    // ========================================

    @Test
    void shouldRejectMissingRole() throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/role", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminUserService);
    }

    // ========================================
    // 8. locked 누락 시 400 Bad Request
    // ========================================

    @Test
    void shouldRejectMissingLocked() throws Exception {

        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        withCsrf(
                                patch("/api/admin/users/{userId}/locked", userId),
                                csrfCookie
                        )
                                .with(jwt()
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminUserService);
    }
}