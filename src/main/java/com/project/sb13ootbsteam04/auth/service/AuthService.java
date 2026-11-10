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
        User user = userRepository.findByEmailForUpdate(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(LOGIN_FAILURE_MESSAGE)
                );

        // 2. 비밀번호 검증
        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new BadCredentialsException(LOGIN_FAILURE_MESSAGE);
        }

        // 3. 계정 잠금 여부 확인
        if (user.isLocked()) {
            throw new BadCredentialsException(LOGIN_FAILURE_MESSAGE);
        }

        // 4. 로그인 성공 시 버전 증가
        user.increaseLoginVersion();

        // 5. 새로운 로그인 버전이 포함된 JWT 발급
        String accessToken =
                jwtTokenProvider.generateAccessToken(user);

        // 6. 로그인 응답 반환
        return new LoginResponse(accessToken, "Bearer");
    }
}
