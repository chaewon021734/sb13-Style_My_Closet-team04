package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.exception.LoginAttemptBlockedException;
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

        // ========================================
        // 1. 계정별 로그인 일시 차단 여부 확인
        // ========================================

        if (loginAttemptService.isBlocked(email)) {

            // 차단 해제까지 남은 시간(초)
            long retryAfterSeconds =
                    loginAttemptService.getRetryAfterSeconds(email);

            throw new LoginAttemptBlockedException(
                    LOGIN_BLOCK_MESSAGE,
                    retryAfterSeconds
            );
        }

        // ========================================
        // 2. 사용자 조회 및 비관적 쓰기 락 획득
        // ========================================

        User user = userRepository.findByEmailForUpdate(email)
                .orElse(null);

        // ========================================
        // 3. 존재하지 않는 이메일 로그인 실패 처리
        // ========================================

        if (user == null) {

            loginAttemptService.recordFailure(email);

            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // ========================================
        // 4. 관리자에 의해 잠긴 계정 로그인 차단
        // ========================================

        if (user.isLocked()) {

            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // ========================================
        // 5. 일반 비밀번호 검증
        // ========================================

        boolean normalPasswordMatches = passwordEncoder.matches(
                request.password(),
                user.getPassword()
        );

        // ========================================
        // 6. 임시 비밀번호 검증
        // ========================================

        boolean temporaryPasswordMatches = false;

        // 일반 비밀번호가 일치하지 않고,
        // 유효한 임시 비밀번호가 존재하는 경우에만 검증
        if (!normalPasswordMatches
                && user.isTemporaryPasswordActive(Instant.now())) {

            temporaryPasswordMatches = passwordEncoder.matches(
                    request.password(),
                    user.getTemporaryPasswordHash()
            );
        }

        // ========================================
        // 7. 로그인 실패 횟수 기록
        // ========================================

        if (!normalPasswordMatches && !temporaryPasswordMatches) {

            loginAttemptService.recordFailure(email);

            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // ========================================
        // 8. 정상 로그인 시 실패 기록 초기화
        // ========================================

        loginAttemptService.recordSuccess(email);

        // ========================================
        // 9. 로그인 버전 증가
        // ========================================

        // 이전 JWT 무효화를 위해 버전 증가
        user.increaseLoginVersion();

        // ========================================
        // 10. 인증 방식에 따른 JWT 발급
        // ========================================

        String accessToken;

        if (temporaryPasswordMatches) {

            // 임시 비밀번호 로그인
            // authType = TEMPORARY
            accessToken =
                    jwtTokenProvider.generateTemporaryAccessToken(user);

        } else {

            // 일반 비밀번호 로그인
            // authType = NORMAL
            accessToken =
                    jwtTokenProvider.generateAccessToken(user);
        }

        // ========================================
        // 11. 로그인 성공 응답
        // ========================================

        return new LoginResponse(
                accessToken,
                "Bearer"
        );
    }
}
