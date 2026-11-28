package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
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
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResult login(LoginRequest request) {

        User user = userRepository
                .findByEmailForUpdate(request.email())
                .orElseThrow(() ->
                        new BadCredentialsException(
                                LOGIN_FAILURE_MESSAGE
                        )
                );

        if (user.isLocked()) {
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        boolean normalPasswordMatches = passwordEncoder.matches(
                request.password(),
                user.getPassword()
        );

        boolean temporaryPasswordMatches = false;

        if (!normalPasswordMatches
                && user.isTemporaryPasswordActive(Instant.now())) {

            temporaryPasswordMatches = passwordEncoder.matches(
                    request.password(),
                    user.getTemporaryPasswordHash()
            );
        }

        if (!normalPasswordMatches && !temporaryPasswordMatches) {
            throw new BadCredentialsException(
                    LOGIN_FAILURE_MESSAGE
            );
        }

        // 기존 로그인 토큰 무효화
        user.increaseLoginVersion();

        String accessToken;

        RefreshTokenService.IssuedRefreshToken refreshToken = null;

        if (temporaryPasswordMatches) {

            // 임시 비밀번호 로그인 시 제한된 Access Token만 발급
            accessToken = jwtTokenProvider
                    .generateTemporaryAccessToken(user);

        } else {

            // 정상 로그인 시 Access Token 발급
            accessToken = jwtTokenProvider
                    .generateAccessToken(user);

            // 정상 로그인 시 Refresh Token 발급
            refreshToken = refreshTokenService.issue(user);
        }

        UserDto userDto = UserDto.from(user);

        LoginResponse response = new LoginResponse(
                accessToken,
                "Bearer",
                userDto
        );

        return new LoginResult(response, refreshToken);
    }
}