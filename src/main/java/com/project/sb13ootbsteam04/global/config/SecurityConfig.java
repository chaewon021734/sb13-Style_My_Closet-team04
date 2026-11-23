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
                // JWT REST API - 현재 CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // 세션을 생성하지 않는 JWT 인증
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth

                        // 오류 경로 허용
                        .requestMatchers("/error")
                        .permitAll()

                        // 회원가입 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).permitAll()

                        // 로그인 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // 비밀번호 재설정 API
                        // 임시 비밀번호 인증 및 일반 인증 사용자 허용
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/auth/password"
                        ).hasAnyRole(
                                "PASSWORD_RESET",
                                "USER",
                                "ADMIN"
                        )

                        // 관리자 API는 ADMIN만 접근
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // 그 외 API는 일반 로그인 사용자만 접근
                        // 임시 비밀번호 인증 사용자는 접근 불가
                        .anyRequest()
                        .hasAnyRole("USER", "ADMIN")
                )

                // JWT Bearer Token 인증
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
    }

    // JWT 정보를 Spring Security 권한으로 변환
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String authType = jwt.getClaimAsString("authType");

            // 임시 비밀번호로 인증한 사용자는
            // 비밀번호 재설정 권한만 부여
            if ("TEMPORARY".equals(authType)) {

                return List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_PASSWORD_RESET"
                        )
                );
            }

            // 알 수 없는 인증 유형은 권한 부여하지 않음
            if (authType != null
                    && !"NORMAL".equals(authType)) {
                return List.of();
            }

            // 일반 로그인 JWT의 사용자 권한 확인
            String role = jwt.getClaimAsString("role");

            if ("USER".equals(role)) {
                return List.of(
                        new SimpleGrantedAuthority("ROLE_USER")
                );
            }

            if ("ADMIN".equals(role)) {
                return List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
            }

            // 유효하지 않은 역할은 권한 없음
            return List.of();
        });

        return converter;
    }
}
