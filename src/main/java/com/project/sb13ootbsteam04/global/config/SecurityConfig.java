package com.project.sb13ootbsteam04.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
                // SPA 프론트엔드 환경에 맞는 CSRF 보호 설정
                // XSRF-TOKEN 쿠키 발급
                // X-XSRF-TOKEN 요청 헤더 검증
                .csrf(csrf -> csrf.spa())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .authorizeHttpRequests(auth -> auth

                        // 프론트엔드 정적 리소스 접근 허용
                        // 로그인 이전에도 HTML, JavaScript, CSS, 이미지 접근 가능
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/logo_symbol.svg",
                                "/vite.svg"
                        ).permitAll()

                        // 오류 경로
                        .requestMatchers("/error").permitAll()

                        // Swagger UI 및 OpenAPI
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // 회원가입
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).permitAll()

                        // 기존 로그인 API 및 프론트엔드 로그인 API 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/sign-in"
                        ).permitAll()

                        // CSRF 토큰 발급 API
                        // 로그인 이전에도 접근할 수 있도록 허용
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf-token"
                        ).permitAll()

                        // PR C - 임시 비밀번호 발급
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/temporary-password"
                        ).permitAll()

                        // PR C - 비밀번호 재설정
                        // 임시 JWT에만 부여되는 권한
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/auth/password"
                        ).hasAuthority("PASSWORD_RESET")

                        // PR B - 관리자 API
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 의상 속성 정의 생성
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/clothes/attribute-defs"
                        ).hasRole("ADMIN")

                        // 의상 속성 정의 변경
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/clothes/attribute-defs/*"
                        ).hasRole("ADMIN")

                        // 의상 속성 정의 삭제
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/api/clothes/attribute-defs/*"
                        ).hasRole("ADMIN")

                        // 나머지 API는 정상 인증 필요
                        // 임시 JWT로는 접근 불가
                        .anyRequest().access((authentication, context) -> {

                            Authentication current = authentication.get();

                            if (current == null
                                    || !current.isAuthenticated()
                                    || current instanceof AnonymousAuthenticationToken) {
                                return new AuthorizationDecision(false);
                            }

                            if (current instanceof JwtAuthenticationToken jwtAuth) {

                                String authType = jwtAuth.getToken()
                                        .getClaimAsString("authType");

                                if ("TEMPORARY".equals(authType)) {
                                    return new AuthorizationDecision(false);
                                }
                            }

                            return new AuthorizationDecision(true);
                        })
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    // JWT의 인증 유형과 역할을 Spring Security 권한으로 변환
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String authType =
                    jwt.getClaimAsString("authType");

            // 임시 JWT에는 사용자 및 관리자 권한을 부여하지 않음
            if ("TEMPORARY".equals(authType)) {
                return List.of(
                        new SimpleGrantedAuthority("PASSWORD_RESET")
                );
            }

            // 일반 JWT는 기존 권한 유지
            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return List.of();
            }

            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
        });

        return converter;
    }
}