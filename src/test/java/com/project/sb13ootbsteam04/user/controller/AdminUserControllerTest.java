package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.global.config.SecurityConfig;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.dto.response.UserDtoCursorResponse;
import com.project.sb13ootbsteam04.user.entity.UserRole;
import com.project.sb13ootbsteam04.user.exception.UserException;
import com.project.sb13ootbsteam04.user.service.AdminUserService;
import com.project.sb13ootbsteam04.user.service.UserService;

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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.issueCsrfCookie;
import static com.project.sb13ootbsteam04.global.support.CsrfTestSupport.withCsrf;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AdminUserController.class,
        UserController.class,
        CsrfController.class
})
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private UserService userService;

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

        when(adminUserService.changeRole(userId, UserRole.ADMIN))
                .thenReturn(userDto(userId, "ADMIN", false));

        // 프론트가 응답의 role로 목록을 갱신하므로 200 + UserDto 확인
        mockMvc.perform(
                        withCsrf(
                                patch("/api/users/{userId}/role", userId),
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"));

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

        when(adminUserService.changeLocked(userId, true, currentAdminId))
                .thenReturn(userDto(userId, "USER", true));

        mockMvc.perform(
                        withCsrf(
                                patch("/api/users/{userId}/lock", userId),
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.locked").value(true));

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

        when(adminUserService.changeLocked(userId, false, currentAdminId))
                .thenReturn(userDto(userId, "USER", false));

        mockMvc.perform(
                        withCsrf(
                                patch("/api/users/{userId}/lock", userId),
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

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
                                patch("/api/users/{userId}/role", userId),
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
                                patch("/api/users/{userId}/role", userId),
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
                                patch("/api/users/{userId}/lock", userId),
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
                                patch("/api/users/{userId}/role", userId),
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
                                patch("/api/users/{userId}/lock", userId),
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
    // 9. 이전 관리자 경로 호출 시 404
    // ========================================

    @Test
    void legacyAdminPathIsNoLongerServed() throws Exception {

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
                                .content("{\"locked\": true}")
                )
                .andExpect(status().isNotFound());

        verifyNoInteractions(adminUserService);
    }

    // ========================================
    // 10. 사용자 목록 조회는 ADMIN만 가능
    // ========================================

    @Test
    void regularUserCannotListUsers() throws Exception {

        mockMvc.perform(
                        get("/api/users")
                                .param("limit", "20")
                                .param("sortBy", "createdAt")
                                .param("sortDirection", "DESCENDING")
                                .with(jwt()
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_USER"
                                                )
                                        )
                                )
                )
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void adminCanListUsers() throws Exception {

        when(userService.searchUsers(any()))
                .thenReturn(new UserDtoCursorResponse(
                        List.of(),
                        null,
                        null,
                        false,
                        0L,
                        "createdAt",
                        "DESCENDING"
                ));

        mockMvc.perform(
                        get("/api/users")
                                .param("limit", "20")
                                .param("sortBy", "createdAt")
                                .param("sortDirection", "DESCENDING")
                                .with(jwt()
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_ADMIN"
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    // ========================================
    // 11. 존재하지 않는 사용자는 404
    // ========================================

    @Test
    void shouldReturnNotFoundForMissingUser() throws Exception {

        UUID userId = UUID.randomUUID();

        when(adminUserService.changeRole(userId, UserRole.ADMIN))
                .thenThrow(UserException.userNotFound(userId));

        mockMvc.perform(
                        withCsrf(
                                patch("/api/users/{userId}/role", userId),
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
                                .content("{\"role\": \"ADMIN\"}")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 사용자입니다."))
                .andExpect(jsonPath("$.details.userId").value(userId.toString()));
    }

    private UserDto userDto(UUID userId, String role, boolean locked) {

        return new UserDto(
                userId,
                Instant.parse("2026-11-28T00:00:00Z"),
                "user@test.io",
                "사용자",
                role,
                locked
        );
    }
}