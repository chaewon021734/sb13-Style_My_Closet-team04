package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        loginRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );
    }

    // 1. 정상 로그인
    @Test
    void shouldLoginSuccessfully() {

        // given
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // when
        LoginResponse response = authService.login(loginRequest);

        // then
        assertEquals("test.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());

        verify(userRepository).findByEmail("test@example.com");

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        verify(jwtTokenProvider).generateAccessToken(user);
    }

    // 2. 존재하지 않는 이메일
    @Test
    void shouldRejectUnknownEmail() {

        // given
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());

        // when & then
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // 사용자가 없으면 비밀번호 검사와 JWT 발급을 하지 않음
        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider
        );
    }

    // 3. 비밀번호가 틀린 경우
    @Test
    void shouldRejectWrongPassword() {

        // given
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // when & then
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // 비밀번호가 틀리면 JWT를 발급하지 않음
        verify(jwtTokenProvider, never())
                .generateAccessToken(any(User.class));
    }

    // 4. 잠긴 계정으로 로그인하는 경우
    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        // given
        // 사용자 계정을 잠금 상태로 변경
        ReflectionTestUtils.setField(user, "locked", true);

        // 이메일 조회 시 잠긴 사용자 반환
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // when
        LockedException exception = assertThrows(
                LockedException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals("잠긴 계정입니다.", exception.getMessage());

        // 잠긴 계정은 JWT가 발급되면 안 됨
        verifyNoInteractions(jwtTokenProvider);

        // 현재 AuthService는 비밀번호 검사 전에 잠금 여부를 확인함
        verifyNoInteractions(passwordEncoder);
    }
}
