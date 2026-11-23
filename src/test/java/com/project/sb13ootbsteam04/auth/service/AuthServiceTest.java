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

    // 1. 일반 비밀번호 로그인 성공
    @Test
    void shouldLoginSuccessfully() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        LoginResponse response = authService.login(loginRequest);

        assertEquals("test.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(1L, user.getLoginVersion());

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider).generateAccessToken(user);
    }

    // 2. 존재하지 않는 이메일 로그인 차단
    @Test
    void shouldRejectUnknownEmail() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(passwordEncoder);
    }

    // 3. 잘못된 일반 비밀번호 로그인 차단
    @Test
    void shouldRejectWrongPassword() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider, never())
                .generateAccessToken(any(User.class));
    }

    // 4. 잠긴 계정 + 정상 비밀번호 로그인 차단
    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // 5. 잠긴 계정 + 잘못된 비밀번호 로그인 차단
    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // 6. 일반 비밀번호 재로그인 시 버전 증가
    @Test
    void shouldIncreaseLoginVersionOnRelogin() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // 첫 번째 로그인
        authService.login(loginRequest);
        assertEquals(1L, user.getLoginVersion());

        // 두 번째 로그인
        authService.login(loginRequest);
        assertEquals(2L, user.getLoginVersion());

        verify(userRepository, times(2))
                .findByEmailForUpdate("test@example.com");

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);
    }

    // 7. 로그인 버전 증가 후 일반 JWT 발급
    @Test
    void shouldIncreaseVersionBeforeGeneratingToken() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenAnswer(invocation -> {

                    User tokenUser = invocation.getArgument(0);

                    assertEquals(1L, tokenUser.getLoginVersion());

                    return "test.jwt.token";
                });

        LoginResponse response = authService.login(loginRequest);

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

    // 8. 유효한 임시 비밀번호 로그인 성공
    @Test
    void shouldLoginWithValidTemporaryPassword() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        // 임시 비밀번호 발급 시 버전 1
        assertEquals(1L, user.getLoginVersion());

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword, "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword, "encodedTemporaryPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenReturn("temporary.jwt.token");

        LoginResponse response = authService.login(temporaryRequest);

        assertEquals("temporary.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());

        // 발급 +1, 임시 로그인 성공 +1
        assertEquals(2L, user.getLoginVersion());

        assertEquals("encodedPassword", user.getPassword());

        verify(passwordEncoder)
                .matches(temporaryPassword, "encodedTemporaryPassword");

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(user);
    }

    // 9. 만료된 임시 비밀번호 로그인 차단
    @Test
    void shouldRejectExpiredTemporaryPassword() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(1)
        );

        long versionBeforeLogin = user.getLoginVersion();

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword, "encodedPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 발급 시 증가한 버전 1 유지
        assertEquals(1L, versionBeforeLogin);
        assertEquals(versionBeforeLogin, user.getLoginVersion());

        verify(passwordEncoder, never())
                .matches(temporaryPassword, "encodedTemporaryPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // 10. 잘못된 임시 비밀번호 로그인 차단
    @Test
    void shouldRejectWrongTemporaryPassword() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        long versionBeforeLogin = user.getLoginVersion();

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                "WrongTemp123!"
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongTemp123!", "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                "WrongTemp123!", "encodedTemporaryPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 로그인 실패이므로 추가 버전 증가 없음
        assertEquals(1L, versionBeforeLogin);
        assertEquals(versionBeforeLogin, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("WrongTemp123!", "encodedTemporaryPassword");

        verifyNoInteractions(jwtTokenProvider);
    }

    // 11. 잠긴 계정의 임시 비밀번호 로그인 차단
    @Test
    void shouldRejectTemporaryPasswordLoginWhenAccountIsLocked() {

        String temporaryPassword = "Temp1234!abc";

        user.changeLocked(true);

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        long versionBeforeLogin = user.getLoginVersion();

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword, "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword, "encodedTemporaryPassword"
        )).thenReturn(true);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        // 발급 당시 버전 1 유지
        assertEquals(1L, versionBeforeLogin);
        assertEquals(versionBeforeLogin, user.getLoginVersion());

        verifyNoInteractions(jwtTokenProvider);
    }

    // 12. 임시 비밀번호 로그인 시 버전 증가 후 JWT 발급
    @Test
    void shouldIncreaseLoginVersionOnTemporaryPasswordLogin() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        // 발급 직후 버전 1
        assertEquals(1L, user.getLoginVersion());

        LoginRequest temporaryRequest = new LoginRequest(
                "test@example.com",
                temporaryPassword
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                temporaryPassword, "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword, "encodedTemporaryPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenAnswer(invocation -> {

                    User tokenUser = invocation.getArgument(0);

                    // 발급 +1, 로그인 +1
                    // JWT 생성 시점에는 버전 2여야 함
                    assertEquals(2L, tokenUser.getLoginVersion());

                    return "temporary.jwt.token";
                });

        LoginResponse response = authService.login(temporaryRequest);

        assertEquals("temporary.jwt.token", response.accessToken());
        assertEquals(2L, user.getLoginVersion());

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(user);
    }
}
