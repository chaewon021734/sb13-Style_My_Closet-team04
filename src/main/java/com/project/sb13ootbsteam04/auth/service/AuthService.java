package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 1. 사용자 조회 및 비관적 쓰기 락 획득
        User user = userRepository
                .findByEmailForUpdate(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(
                                LOGIN_FAILURE_MESSAGE
                        )
                );

        // 2. 계정 잠금 여부 검증
        if (user.isLocked()) {
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 3. 일반 비밀번호 검증
        boolean normalPasswordMatches =
                passwordEncoder.matches(
                        request.password(),
                        user.getPassword()
                );

        // 4. 임시 비밀번호 검증
        boolean temporaryPasswordMatches = false;

        if (!normalPasswordMatches
                && user.isTemporaryPasswordActive(Instant.now())) {

            temporaryPasswordMatches = passwordEncoder.matches(
                    request.password(),
                    user.getTemporaryPasswordHash()
            );
        }

        // 5. 두 비밀번호 모두 일치하지 않으면 로그인 실패
        if (!normalPasswordMatches && !temporaryPasswordMatches) {
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 6. 기존 JWT 무효화를 위한 로그인 버전 증가
        user.increaseLoginVersion();

        // 7. 인증 방식에 따라 JWT 발급
        String accessToken;

        if (temporaryPasswordMatches) {

            // 임시 JWT는 비밀번호 재설정 전용
            accessToken =
                    jwtTokenProvider.generateTemporaryAccessToken(user);

        } else {

            // 일반 JWT
            accessToken =
                    jwtTokenProvider.generateAccessToken(user);
        }

        // 8. 로그인 응답
        return new LoginResponse(
                accessToken,
                "Bearer"
        );
    }
}
