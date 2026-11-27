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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        String collection = "/api/clothes/attribute-defs";
        UUID id = UUID.randomUUID();
        String item = collection + "/" + id;
        var user = user("member").roles("USER");

        // when & then
        mockMvc.perform(delete(item)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(collection).with(user)).andExpect(status().isForbidden());
        mockMvc.perform(patch(item).with(user)).andExpect(status().isForbidden());
        mockMvc.perform(delete(item).with(user)).andExpect(status().isForbidden());
        verifyNoInteractions(clothesService);
        mockMvc.perform(get(collection).param("sortBy", "name").param("sortDirection", "ASCENDING").with(user))
                .andExpect(status().isOk());
        mockMvc.perform(delete(item).with(user("admin").roles("ADMIN"))).andExpect(status().isNoContent());
        verify(clothesService).delete(id);
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
