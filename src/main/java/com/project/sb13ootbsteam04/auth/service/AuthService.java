package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private static final String LOGIN_BLOCK_MESSAGE =
            "로그인 시도 횟수가 초과되었습니다. 잠시 후 다시 시도해 주세요.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    // 계정별 로그인 실패 횟수 관리
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public LoginResponse login(LoginRequest request) {

        String email = request.email();

        // 1. 일시 차단된 계정인지 확인
        if (loginAttemptService.isBlocked(email)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    LOGIN_BLOCK_MESSAGE
            );
        }

        // 2. 사용자 조회 및 비관적 쓰기 락 획득
        User user = userRepository.findByEmailForUpdate(email)
                .orElse(null);

        // 3. 등록되지 않은 이메일도 실패 기록
        if (user == null) {
            loginAttemptService.recordFailure(email);
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 4. 관리자에 의해 잠긴 계정은 로그인 차단
        if (user.isLocked()) {
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 5. 일반 비밀번호 검증
        boolean normalPasswordMatches = passwordEncoder.matches(
                request.password(),
                user.getPassword()
        );

        // 6. 일반 비밀번호가 틀리면 임시 비밀번호 검증
        boolean temporaryPasswordMatches = false;

        if (!normalPasswordMatches
                && user.isTemporaryPasswordActive(Instant.now())) {

            temporaryPasswordMatches = passwordEncoder.matches(
                    request.password(),
                    user.getTemporaryPasswordHash()
            );
        }

        // 7. 두 비밀번호가 모두 일치하지 않으면 실패 기록
        if (!normalPasswordMatches && !temporaryPasswordMatches) {

            loginAttemptService.recordFailure(email);

            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 8. 정상 로그인 시 실패 기록 초기화
        loginAttemptService.recordSuccess(email);

        // 9. JWT 무효화 버전 증가
        user.increaseLoginVersion();

        // 10. 인증 방식에 따른 JWT 생성
        String accessToken;

        if (temporaryPasswordMatches) {

            // 임시 비밀번호 인증
            accessToken =
                    jwtTokenProvider.generateTemporaryAccessToken(user);

        } else {

            // 일반 비밀번호 인증
            accessToken =
                    jwtTokenProvider.generateAccessToken(user);
        }

        // 11. 로그인 성공 응답
        return new LoginResponse(accessToken, "Bearer");
    }
}
