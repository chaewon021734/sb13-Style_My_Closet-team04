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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        // 1. 이메일로 사용자 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(LOGIN_FAILURE_MESSAGE)
                );

        // 2. 비밀번호 먼저 검증
        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new BadCredentialsException(LOGIN_FAILURE_MESSAGE);
        }

        // 3. 계정 잠금 여부 확인
        // 외부에 잠금 상태를 노출하지 않도록 동일한 인증 실패 예외 사용
        if (user.isLocked()) {
            throw new BadCredentialsException(LOGIN_FAILURE_MESSAGE);
        }

        // 4. JWT Access Token 발급
        String accessToken =
                jwtTokenProvider.generateAccessToken(user);

        // 5. 로그인 응답 반환
        return new LoginResponse(accessToken, "Bearer");
    }
}
