package com.project.sb13ootbsteam04.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 회원가입 API는 CSRF 검사 제외
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/users")
                )

                // API 접근 권한 설정
                .authorizeHttpRequests(auth -> auth

                        // 오류 처리 경로 접근 허용
                        .requestMatchers("/error").permitAll()

                        // 회원가입은 로그인 없이 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/users"
                        ).permitAll()

                        // 나머지 API는 인증 필요
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
