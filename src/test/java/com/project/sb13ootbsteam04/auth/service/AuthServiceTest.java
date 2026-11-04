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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

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

    // ========================================
    // 1. 정상 로그인
    // ========================================

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

    // ========================================
    // 2. 존재하지 않는 이메일
    // ========================================

    @Test
    void shouldRejectUnknownEmail() {

        // given
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 사용자가 없으면 JWT를 발급하지 않음
        verifyNoInteractions(jwtTokenProvider);

        // 현재 구현에서는 존재하지 않는 이메일에 대해
        // 비밀번호 비교를 수행하지 않음
        verifyNoInteractions(passwordEncoder);
    }

    // ========================================
    // 3. 비밀번호가 틀린 경우
    // ========================================

    @Test
    void shouldRejectWrongPassword() {

        // given
        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        // 비밀번호가 틀리면 JWT를 발급하지 않음
        verify(jwtTokenProvider, never())
                .generateAccessToken(any(User.class));
    }

    // ========================================
    // 4. 잠긴 계정 + 올바른 비밀번호
    // ========================================

    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        // given
        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // 비밀번호 검증은 성공
        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 잠금 상태 확인 전에 비밀번호 검증을 수행했는지 확인
        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        // 잠긴 계정에는 JWT를 발급하지 않음
        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 5. 잠긴 계정 + 잘못된 비밀번호
    // ========================================

    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        // given
        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        // 비밀번호 검증 실패
        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        // 잠긴 계정 + 잘못된 비밀번호에서도 JWT 발급 금지
        verifyNoInteractions(jwtTokenProvider);
    }
}
