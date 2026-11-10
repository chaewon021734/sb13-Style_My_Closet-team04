
package com.project.sb13ootbsteam04.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {

        http
                // JWT를 사용하는 REST API이므로 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // HTTP 세션을 생성하지 않는 무상태 인증
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth

                        // 오류 경로 허용
                        .requestMatchers("/error").permitAll()

                        // Swagger UI 및 OpenAPI 문서 접근 허용
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 회원가입은 인증 없이 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).permitAll()

                        // 로그인은 인증 없이 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // 나머지 API는 JWT 인증 필수
                        .anyRequest().authenticated()
                )

                // JWT Bearer Token 인증 활성화
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    // JWT의 role 클레임을 Spring Security 권한으로 변환
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            // JWT에서 role 값 가져오기
            String role = jwt.getClaimAsString("role");

            // role이 없거나 비어 있으면 권한 부여하지 않음
            if (role == null || role.isBlank()) {
                return List.of();
            }

            // USER -> ROLE_USER
            // ADMIN -> ROLE_ADMIN
            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
        });

        return converter;
    }
}
