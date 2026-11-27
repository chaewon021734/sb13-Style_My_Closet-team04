package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.jwt.JwtTokenProvider;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
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
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        long previousVersion = user.getLoginVersion();

        // when
        LoginResponse response = authService.login(loginRequest);

        // then
        assertEquals("test.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());

        // 로그인 성공 시 버전이 1 증가해야 함
        assertEquals(previousVersion + 1, user.getLoginVersion());

        verify(userRepository).findByEmailForUpdate("test@example.com");

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
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.empty());

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        verify(userRepository).findByEmailForUpdate("test@example.com");

        // 사용자가 없으면 비밀번호 비교 및 JWT 발급 금지
        verifyNoInteractions(passwordEncoder, jwtTokenProvider);
    }

    // ========================================
    // 3. 비밀번호가 틀린 경우
    // ========================================

    @Test
    void shouldRejectWrongPassword() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        long previousVersion = user.getLoginVersion();

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 로그인 실패 시 버전은 변경되면 안 됨
        assertEquals(previousVersion, user.getLoginVersion());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

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

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        long previousVersion = user.getLoginVersion();

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 잠긴 계정은 로그인 버전 증가 금지
        assertEquals(previousVersion, user.getLoginVersion());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 5. 잠긴 계정 + 잘못된 비밀번호
    // ========================================

    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        // given
        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        long previousVersion = user.getLoginVersion();

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        assertEquals(previousVersion, user.getLoginVersion());

        verify(passwordEncoder).matches(
                "password123",
                "encodedPassword"
        );

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 6. 재로그인 시 로그인 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionOnEverySuccessfulLogin() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // when
        authService.login(loginRequest);
        long firstLoginVersion = user.getLoginVersion();

        authService.login(loginRequest);
        long secondLoginVersion = user.getLoginVersion();

        // then
        assertEquals(1L, firstLoginVersion);
        assertEquals(2L, secondLoginVersion);

        verify(userRepository, times(2))
                .findByEmailForUpdate("test@example.com");

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);
    }

    // ========================================
    // 7. JWT 발급 전 로그인 버전 증가 검증
    // ========================================

    @Test
    void shouldIncreaseLoginVersionBeforeIssuingJwt() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenAnswer(invocation -> {
                    // JWT 발급 시점에 이미 버전이 증가해 있어야 함
                    assertEquals(1L, user.getLoginVersion());
                    return "test.jwt.token";
                });

        // when
        authService.login(loginRequest);

        // then
        assertEquals(1L, user.getLoginVersion());

        InOrder inOrder = inOrder(
                userRepository,
                passwordEncoder,
                jwtTokenProvider
        );

        inOrder.verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        inOrder.verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        inOrder.verify(jwtTokenProvider)
                .generateAccessToken(user);
    }
}
