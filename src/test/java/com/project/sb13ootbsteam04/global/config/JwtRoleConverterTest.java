package com.project.sb13ootbsteam04.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtRoleConverterTest {

    private final JwtAuthenticationConverter converter =
            new SecurityConfig().jwtAuthenticationConverter();

    // 테스트용 JWT 생성
    private Jwt createJwt(String role) {

        Instant now = Instant.now();

        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("test-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600));

        if (role != null) {
            builder.claim("role", role);
        }

        return builder.build();
    }

    // USER -> ROLE_USER
    @Test
    void shouldConvertUserRole() {

        Jwt jwt = createJwt("USER");

        AbstractAuthenticationToken authentication =
                converter.convert(jwt);

        assertThat(authentication).isNotNull();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_USER");
    }

    // ADMIN -> ROLE_ADMIN
    @Test
    void shouldConvertAdminRole() {

        Jwt jwt = createJwt("ADMIN");

        AbstractAuthenticationToken authentication =
                converter.convert(jwt);

        assertThat(authentication).isNotNull();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMIN");
    }

    // role이 없으면 ROLE_ 권한이 없어야 함
    @Test
    void shouldReturnNoRoleAuthoritiesWhenRoleMissing() {

        Jwt jwt = createJwt(null);

        AbstractAuthenticationToken authentication =
                converter.convert(jwt);

        assertThat(authentication).isNotNull();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority ->
                        authority.startsWith("ROLE_")
                );
    }
}
