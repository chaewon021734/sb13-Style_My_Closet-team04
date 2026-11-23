package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
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

import java.time.Instant;
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
    // 1. 정상 비밀번호 로그인
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

        // when
        LoginResponse response = authService.login(loginRequest);

        // then
        assertEquals("test.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());

        assertEquals(1L, user.getLoginVersion());

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider).generateAccessToken(user);
    }

    // ========================================
    // 2. 존재하지 않는 이메일 로그인 차단
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

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(passwordEncoder);
    }

    // ========================================
    // 3. 잘못된 일반 비밀번호 로그인 차단
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

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider, never())
                .generateAccessToken(any(User.class));
    }

    // ========================================
    // 4. 잠긴 계정 + 정상 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        // given
        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

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

        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 5. 잠긴 계정 + 잘못된 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        // given
        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
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

        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 6. 재로그인 시 로그인 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionOnRelogin() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // 첫 번째 로그인
        authService.login(loginRequest);

        assertEquals(1L, user.getLoginVersion());

        // 두 번째 로그인
        authService.login(loginRequest);

        // then
        assertEquals(2L, user.getLoginVersion());

        verify(userRepository, times(2))
                .findByEmailForUpdate("test@example.com");

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);
    }

    // ========================================
    // 7. 로그인 버전 증가 후 JWT 발급
    // ========================================

    @Test
    void shouldIncreaseVersionBeforeGeneratingToken() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenAnswer(invocation -> {

                    User tokenUser = invocation.getArgument(0);

                    assertEquals(1L, tokenUser.getLoginVersion());

                    return "test.jwt.token";
                });

        // when
        LoginResponse response = authService.login(loginRequest);

        // then
        assertEquals("test.jwt.token", response.accessToken());

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

    // ========================================
    // 8. 유효한 임시 비밀번호 로그인 성공
    // ========================================

    @Test
    void shouldLoginWithValidTemporaryPassword() {

        // given
        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        // 일반 비밀번호는 불일치
        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        // 임시 비밀번호는 일치
        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedTemporaryPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenReturn("temporary.jwt.token");

        // when
        LoginResponse response = authService.login(temporaryRequest);

        // then
        assertEquals("temporary.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());

        assertEquals(1L, user.getLoginVersion());

        // 기존 비밀번호는 유지
        assertEquals("encodedPassword", user.getPassword());

        verify(passwordEncoder)
                .matches(temporaryPassword, "encodedTemporaryPassword");

        verify(jwtTokenProvider).generateTemporaryAccessToken(user);

        // 일반 JWT는 발급하지 않아야 함
        verify(jwtTokenProvider, never()).generateAccessToken(user);
    }

    // ========================================
    // 9. 만료된 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectExpiredTemporaryPassword() {

        // given
        String temporaryPassword = "Temp1234!abc";

        // 이미 1초 전에 만료된 임시 비밀번호
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(1)
        );

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        assertEquals(0L, user.getLoginVersion());

        // 만료되었으므로 임시 비밀번호 해시와 비교하지 않음
        verify(passwordEncoder, never())
                .matches(temporaryPassword, "encodedTemporaryPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 10. 잘못된 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectWrongTemporaryPassword() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                "WrongTemp123!"
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongTemp123!",
                "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                "WrongTemp123!",
                "encodedTemporaryPassword"
        )).thenReturn(false);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("WrongTemp123!", "encodedTemporaryPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 11. 잠긴 계정은 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectTemporaryPasswordLoginWhenAccountIsLocked() {

        // given
        String temporaryPassword = "Temp1234!abc";

        user.changeLocked(true);

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedTemporaryPassword"
        )).thenReturn(true);

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 12. 임시 비밀번호 로그인 시 로그인 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionOnTemporaryPasswordLogin() {

        // given
        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedTemporaryPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenAnswer(invocation -> {

                    User tokenUser = invocation.getArgument(0);

                    // JWT 발급 전에 로그인 버전 증가 확인
                    assertEquals(1L, tokenUser.getLoginVersion());

                    return "temporary.jwt.token";
                });

        // when
        LoginResponse response = authService.login(temporaryRequest);

        // then
        assertEquals(
                "temporary.jwt.token",
                response.accessToken()
        );

        assertEquals(1L, user.getLoginVersion());

        verify(jwtTokenProvider).generateTemporaryAccessToken(user);

        // 일반 JWT가 발급되지 않았는지 확인
        verify(jwtTokenProvider, never()).generateAccessToken(user);
    }
}
