package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.service.AdminUserService;

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

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ========================================
    // 1. ADMIN 권한으로 사용자 권한 변경
    // ========================================

    @Test
    void adminCanChangeUserRole() throws Exception {

        // given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", userId)
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

        // given
        UUID userId = UUID.randomUUID();

        // 현재 로그인한 관리자의 UUID
        UUID currentAdminId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": true
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/locked", userId)
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

        // JWT의 관리자 ID가 Service에 전달되는지 확인
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

        // given
        UUID userId = UUID.randomUUID();

        // 현재 로그인한 관리자의 UUID
        UUID currentAdminId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": false
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/locked", userId)
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

        // JWT의 관리자 ID가 Service에 전달되는지 확인
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

        // given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", userId)
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

        // given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "role": "ADMIN"
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", userId)
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

        // given
        UUID userId = UUID.randomUUID();

        String requestBody = """
                {
                    "locked": true
                }
                """;

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/locked", userId)
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
    // 7. 권한 변경 요청에서 role 누락 시 400
    // ========================================

    @Test
    void shouldRejectMissingRole() throws Exception {

        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/role", userId)
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
    // 8. 계정 잠금 요청에서 locked 누락 시 400
    // ========================================

    @Test
    void shouldRejectMissingLocked() throws Exception {

        // given
        UUID userId = UUID.randomUUID();

        // when & then
        mockMvc.perform(
                        patch("/api/admin/users/{userId}/locked", userId)
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
