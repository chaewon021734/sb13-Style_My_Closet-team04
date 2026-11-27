package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.auth.controller.CsrfController;
import com.project.sb13ootbsteam04.auth.service.AuthService;
import com.project.sb13ootbsteam04.clothes.controller.ClothesAttributeDefController;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesAttributeDefMapper;
import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({
        SecurityConfig.class,
        SecurityConfigTest.TestController.class,
        ClothesAttributeDefController.class,
        CsrfController.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private ClothesAttributeDefService clothesService;

    @MockitoBean
    private ClothesAttributeDefMapper clothesMapper;

    // ========================================
    // 1. 기존 의상 속성 정의 권한 테스트
    // ========================================

    @Test
    void attributeDefinitionsRequireAdminForWrites() throws Exception {

        // given
        String definitionsPath = "/api/clothes/attribute-defs";
        UUID definitionId = UUID.randomUUID();
        String definitionPath = definitionsPath + "/" + definitionId;

        // 실제 CSRF 토큰 발급
        Cookie csrfCookie = issueCsrfCookie(mockMvc);

        when(jwtDecoder.decode("member-token"))
                .thenReturn(roleToken("member-token", "USER"));

        when(jwtDecoder.decode("admin-token"))
                .thenReturn(roleToken("admin-token", "ADMIN"));

        String memberAuthorization = "Bearer member-token";

        // 정상 CSRF 토큰이 있더라도 인증 없이 삭제 요청 시 401
        mockMvc.perform(
                withCsrf(delete(definitionPath), csrfCookie)
        ).andExpect(status().isUnauthorized())
         .andExpect(content().string(""));

        // USER 권한으로 생성 요청 시 403
        mockMvc.perform(
                withCsrf(post(definitionsPath), csrfCookie)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                memberAuthorization
                        )
        ).andExpect(status().isForbidden());

        // USER 권한으로 변경 요청 시 403
        mockMvc.perform(
                withCsrf(patch(definitionPath), csrfCookie)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                memberAuthorization
                        )
        ).andExpect(status().isForbidden());

        // USER 권한으로 삭제 요청 시 403
        mockMvc.perform(
                withCsrf(delete(definitionPath), csrfCookie)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                memberAuthorization
                        )
        ).andExpect(status().isForbidden())
         .andExpect(content().string(""));

        // USER의 쓰기 요청 3개가 JWT 인증까지 진행되었는지 확인
        verify(jwtDecoder, times(3)).decode("member-token");

        verifyNoInteractions(clothesService);

        // USER 권한으로 조회 가능
        // GET 요청에는 CSRF 토큰이 필요하지 않음
        mockMvc.perform(
                get(definitionsPath)
                        .param("sortBy", "name")
                        .param("sortDirection", "ASCENDING")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                memberAuthorization
                        )
        ).andExpect(status().isOk());

        // ADMIN 권한으로 삭제 가능
        mockMvc.perform(
                withCsrf(delete(definitionPath), csrfCookie)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer admin-token"
                        )
        ).andExpect(status().isNoContent());

        verify(clothesService).delete(definitionId);
    }

    // ========================================
    // 2. JWT 없이 일반 API 요청 시 401
    // ========================================

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {

        mockMvc.perform(
                get("/api/security-test/secured")
        ).andExpect(status().isUnauthorized());
    }

    // ========================================
    // 3. 잘못된 JWT로 일반 API 요청 시 401
    // ========================================

    @Test
    void shouldRejectInvalidToken() throws Exception {

        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(
                        new BadJwtException("유효하지 않은 JWT")
                );

        mockMvc.perform(
                get("/api/security-test/secured")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        )
        ).andExpect(status().isUnauthorized());
    }

    // ========================================
    // 4. 유효한 JWT로 일반 API 요청 시 200
    // ========================================

    @Test
    void shouldAllowValidToken() throws Exception {

        Instant now = Instant.now();

        Jwt jwt = Jwt.withTokenValue("valid-token")
                .header("alg", "HS256")
                .subject("test-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", "USER")
                .build();

        when(jwtDecoder.decode("valid-token"))
                .thenReturn(jwt);

        mockMvc.perform(
                get("/api/security-test/secured")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer valid-token"
                        )
        ).andExpect(status().isOk());
    }

    // ========================================
    // 5. ADMIN 권한으로 관리자 API 접근 허용
    // ========================================

    @Test
    void shouldAllowAdminAccessToAdminApi() throws Exception {

        when(jwtDecoder.decode("admin-access-token"))
                .thenReturn(
                        roleToken("admin-access-token", "ADMIN")
                );

        mockMvc.perform(
                get("/api/admin/security-test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer admin-access-token"
                        )
        ).andExpect(status().isOk())
         .andExpect(content().string("관리자 인증 성공"));

        verify(jwtDecoder).decode("admin-access-token");
    }

    // ========================================
    // 6. USER 권한으로 관리자 API 접근 시 403
    // ========================================

    @Test
    void shouldRejectRegularUserFromAdminApi() throws Exception {

        when(jwtDecoder.decode("user-access-token"))
                .thenReturn(
                        roleToken("user-access-token", "USER")
                );

        mockMvc.perform(
                get("/api/admin/security-test")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer user-access-token"
                        )
        ).andExpect(status().isForbidden());

        verify(jwtDecoder).decode("user-access-token");
    }

    // ========================================
    // 7. 인증 없이 관리자 API 접근 시 401
    // ========================================

    @Test
    void shouldRejectAnonymousAccessToAdminApi() throws Exception {

        mockMvc.perform(
                get("/api/admin/security-test")
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(jwtDecoder);
    }

    // ========================================
    // 테스트용 JWT 생성
    // ========================================

    private Jwt roleToken(String token, String role) {

        Instant now = Instant.now();

        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("role", role)
                .claim("loginVersion", 0L)
                .build();
    }

    // ========================================
    // 테스트 전용 API
    // ========================================

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