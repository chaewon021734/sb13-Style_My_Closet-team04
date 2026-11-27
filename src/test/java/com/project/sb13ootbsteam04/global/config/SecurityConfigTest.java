package com.project.sb13ootbsteam04.global.config;

import com.project.sb13ootbsteam04.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.sb13ootbsteam04.clothes.service.ClothesAttributeDefService;
import com.project.sb13ootbsteam04.clothes.mapper.ClothesAttributeDefMapper;
import com.project.sb13ootbsteam04.clothes.controller.ClothesAttributeDefController;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({
        SecurityConfig.class,
        SecurityConfigTest.TestController.class,
        ClothesAttributeDefController.class
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

    @Test
    void attributeDefinitionsRequireAdminForWrites() throws Exception {
        // given
        String definitionsPath = "/api/clothes/attribute-defs";
        UUID definitionId = UUID.randomUUID();
        String definitionPath = definitionsPath + "/" + definitionId;

        // 디코딩 이후 운영 필터와 동일한 경로에서 role 클레임을 권한으로 변환한다.
        when(jwtDecoder.decode("member-token")).thenReturn(roleToken("member-token", "USER"));
        when(jwtDecoder.decode("admin-token")).thenReturn(roleToken("admin-token", "ADMIN"));
        String memberAuthorization = "Bearer member-token";

        // when & then
        mockMvc.perform(delete(definitionPath)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mockMvc.perform(post(definitionsPath).header(HttpHeaders.AUTHORIZATION, memberAuthorization)).andExpect(status().isForbidden());
        mockMvc.perform(patch(definitionPath).header(HttpHeaders.AUTHORIZATION, memberAuthorization)).andExpect(status().isForbidden());
        mockMvc.perform(delete(definitionPath).header(HttpHeaders.AUTHORIZATION, memberAuthorization)).andExpect(status().isForbidden()).andExpect(content().string(""));
        verifyNoInteractions(clothesService);
        mockMvc.perform(get(definitionsPath).param("sortBy", "name").param("sortDirection", "ASCENDING").header(HttpHeaders.AUTHORIZATION, memberAuthorization)).andExpect(status().isOk());
        mockMvc.perform(delete(definitionPath).header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")).andExpect(status().isNoContent());
        verify(clothesService).delete(definitionId);
    }

    private Jwt roleToken(String token, String role) {
        return Jwt.withTokenValue(token).header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).claim("role", role).build();
    }

    // 테스트 전용 API
    @RestController
    static class TestController {

        @GetMapping("/api/security-test/secured")
        public ResponseEntity<String> secured() {
            return ResponseEntity.ok("인증 성공");
        }
    }

    // 1. JWT 없이 API 요청 → 401
    @Test
    void shouldRejectRequestWithoutToken() throws Exception {

        mockMvc.perform(
                get("/api/security-test/secured")
        ).andExpect(status().isUnauthorized());
    }

    // 2. 잘못된 JWT로 API 요청 → 401
    @Test
    void shouldRejectInvalidToken() throws Exception {

        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("유효하지 않은 JWT"));

        mockMvc.perform(
                get("/api/security-test/secured")
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        )
        ).andExpect(status().isUnauthorized());
    }

    // 3. 유효한 JWT로 API 요청 → 200
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
                                "Authorization",
                                "Bearer valid-token"
                        )
        ).andExpect(status().isOk());
    }
}
