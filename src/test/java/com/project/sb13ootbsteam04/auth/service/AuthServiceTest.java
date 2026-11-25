package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.request.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.response.LoginResponse;
import com.project.sb13ootbsteam04.auth.exception.LoginAttemptBlockedException;
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

    private static final String EMAIL =
            "test@example.com";

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private static final String LOGIN_BLOCK_MESSAGE =
            "로그인 시도 횟수가 초과되었습니다. 잠시 후 다시 시도해 주세요.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                EMAIL,
                "encodedPassword"
        );

        loginRequest = new LoginRequest(
                EMAIL,
                "password123"
        );
    }

    // ========================================
    // 공통 Mock 설정
    // ========================================

    private void mockUserFound() {
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));
    }

    private void mockNormalPasswordSuccess() {
        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(true);
    }

    private LoginRequest createTemporaryRequest(
            String temporaryPassword
    ) {
        return new LoginRequest(
                EMAIL,
                temporaryPassword
        );
    }

    // ========================================
    // 1. 일반 비밀번호 로그인 성공
    // ========================================

    @Test
    void shouldLoginSuccessfully() {

        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        LoginResponse response =
                authService.login(loginRequest);

        assertEquals(
                "test.jwt.token",
                response.accessToken()
        );

        assertEquals(
                "Bearer",
                response.tokenType()
        );

        assertEquals(1L, user.getLoginVersion());

        verify(userRepository)
                .findByEmailForUpdate(EMAIL);

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider)
                .generateAccessToken(user);

        verify(loginAttemptService)
                .recordSuccess(EMAIL);

        verify(loginAttemptService, never())
                .recordFailure(anyString());
    }

    // ========================================
    // 2. 존재하지 않는 이메일 로그인 차단
    // ========================================

    @Test
    void shouldRejectUnknownEmail() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        verifyNoInteractions(
                jwtTokenProvider,
                passwordEncoder
        );
    }

    // ========================================
    // 3. 잘못된 일반 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectWrongPassword() {

        mockUserFound();

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(0L, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 4. 잠긴 계정 + 정상 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        user.changeLocked(true);
        mockUserFound();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider
        );

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        verify(loginAttemptService, never())
                .recordFailure(anyString());
    }

    // ========================================
    // 5. 잠긴 계정 + 잘못된 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        user.changeLocked(true);
        mockUserFound();

        LoginRequest wrongRequest = new LoginRequest(
                EMAIL,
                "wrongPassword"
        );

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(wrongRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider
        );

        verify(loginAttemptService, never())
                .recordSuccess(anyString());
    }

    // ========================================
    // 6. 일반 비밀번호 재로그인 시 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionOnRelogin() {

        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        authService.login(loginRequest);

        assertEquals(1L, user.getLoginVersion());

        authService.login(loginRequest);

        assertEquals(2L, user.getLoginVersion());

        verify(userRepository, times(2))
                .findByEmailForUpdate(EMAIL);

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);

        verify(loginAttemptService, times(2))
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 7. 버전 증가 후 일반 JWT 발급
    // ========================================

    @Test
    void shouldIncreaseVersionBeforeGeneratingToken() {

        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenAnswer(invocation -> {

                    User tokenUser =
                            invocation.getArgument(0);

                    assertEquals(
                            1L,
                            tokenUser.getLoginVersion()
                    );

                    return "test.jwt.token";
                });

        LoginResponse response =
                authService.login(loginRequest);

        assertEquals(
                "test.jwt.token",
                response.accessToken()
        );

        assertEquals(1L, user.getLoginVersion());

        InOrder inOrder = inOrder(
                loginAttemptService,
                userRepository,
                passwordEncoder,
                jwtTokenProvider
        );

        inOrder.verify(loginAttemptService)
                .isBlocked(EMAIL);

        inOrder.verify(userRepository)
                .findByEmailForUpdate(EMAIL);

        inOrder.verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        inOrder.verify(loginAttemptService)
                .recordSuccess(EMAIL);

        inOrder.verify(jwtTokenProvider)
                .generateAccessToken(user);
    }

    // ========================================
    // 8. 유효한 임시 비밀번호 로그인 성공
    // ========================================

    @Test
    void shouldLoginWithValidTemporaryPassword() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        assertEquals(1L, user.getLoginVersion());

        LoginRequest temporaryRequest =
                createTemporaryRequest(temporaryPassword);

        mockUserFound();

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedTemporaryPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenReturn("temporary.jwt.token");

        LoginResponse response =
                authService.login(temporaryRequest);

        assertEquals(
                "temporary.jwt.token",
                response.accessToken()
        );

        assertEquals(
                "Bearer",
                response.tokenType()
        );

        assertEquals(2L, user.getLoginVersion());

        assertEquals(
                "encodedPassword",
                user.getPassword()
        );

        verify(passwordEncoder)
                .matches(
                        temporaryPassword,
                        "encodedTemporaryPassword"
                );

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(user);

        verify(loginAttemptService)
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 9. 만료된 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectExpiredTemporaryPassword() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(1)
        );

        long versionBeforeLogin =
                user.getLoginVersion();

        LoginRequest temporaryRequest =
                createTemporaryRequest(temporaryPassword);

        mockUserFound();

        when(passwordEncoder.matches(
                temporaryPassword,
                "encodedPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                versionBeforeLogin,
                user.getLoginVersion()
        );

        verify(passwordEncoder, never())
                .matches(
                        temporaryPassword,
                        "encodedTemporaryPassword"
                );

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 10. 잘못된 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectWrongTemporaryPassword() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        long versionBeforeLogin =
                user.getLoginVersion();

        LoginRequest temporaryRequest =
                createTemporaryRequest("WrongTemp123!");

        mockUserFound();

        when(passwordEncoder.matches(
                "WrongTemp123!",
                "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                "WrongTemp123!",
                "encodedTemporaryPassword"
        )).thenReturn(false);

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                versionBeforeLogin,
                user.getLoginVersion()
        );

        verify(passwordEncoder)
                .matches(
                        "WrongTemp123!",
                        "encodedTemporaryPassword"
                );

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 11. 잠긴 계정의 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectTemporaryPasswordLoginWhenAccountIsLocked() {

        String temporaryPassword = "Temp1234!abc";

        user.changeLocked(true);

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        long versionBeforeLogin =
                user.getLoginVersion();

        LoginRequest temporaryRequest =
                createTemporaryRequest(temporaryPassword);

        mockUserFound();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                versionBeforeLogin,
                user.getLoginVersion()
        );

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider
        );

        verify(loginAttemptService, never())
                .recordSuccess(anyString());
    }

    // ========================================
    // 12. 임시 비밀번호 로그인 시 버전 증가 후 JWT 발급
    // ========================================

    @Test
    void shouldIncreaseLoginVersionOnTemporaryPasswordLogin() {

        String temporaryPassword = "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        assertEquals(1L, user.getLoginVersion());

        LoginRequest temporaryRequest =
                createTemporaryRequest(temporaryPassword);

        mockUserFound();

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

                    User tokenUser =
                            invocation.getArgument(0);

                    assertEquals(
                            2L,
                            tokenUser.getLoginVersion()
                    );

                    return "temporary.jwt.token";
                });

        LoginResponse response =
                authService.login(temporaryRequest);

        assertEquals(
                "temporary.jwt.token",
                response.accessToken()
        );

        assertEquals(2L, user.getLoginVersion());

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(user);

        verify(loginAttemptService)
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 13. 계정 일시 차단 시 전용 예외 발생
    // ========================================

    @Test
    void shouldRejectLoginWhenTemporarilyBlocked() {

        // given
        when(loginAttemptService.isBlocked(EMAIL))
                .thenReturn(true);

        when(loginAttemptService.getRetryAfterSeconds(EMAIL))
                .thenReturn(600L);

        // when
        LoginAttemptBlockedException exception = assertThrows(
                LoginAttemptBlockedException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(
                LOGIN_BLOCK_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                600L,
                exception.getRetryAfterSeconds()
        );

        verify(loginAttemptService)
                .isBlocked(EMAIL);

        verify(loginAttemptService)
                .getRetryAfterSeconds(EMAIL);

        // 차단 중에는 DB 조회 및 JWT 발급 없음
        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                jwtTokenProvider
        );

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        verify(loginAttemptService, never())
                .recordFailure(anyString());
    }

    // ========================================
    // 14. 로그인 실패 시 실패 횟수 기록
    // ========================================

    @Test
    void shouldRecordFailureForWrongPassword() {

        mockUserFound();

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        verify(loginAttemptService)
                .isBlocked(EMAIL);

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 15. 정상 로그인 시 실패 기록 초기화
    // ========================================

    @Test
    void shouldClearFailureHistoryAfterSuccessfulLogin() {

        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        LoginResponse response =
                authService.login(loginRequest);

        assertEquals(
                "test.jwt.token",
                response.accessToken()
        );

        verify(loginAttemptService)
                .recordSuccess(EMAIL);

        verify(loginAttemptService, never())
                .recordFailure(anyString());
    }

    // ========================================
    // 16. 실패 횟수 제한 후 다음 로그인 요청 차단
    // ========================================

    @Test
    void shouldRejectLoginAfterFailureLimitReached() {

        // 첫 요청은 차단되지 않고,
        // 두 번째 요청부터 차단된 상태로 가정
        when(loginAttemptService.isBlocked(EMAIL))
                .thenReturn(false, true);

        when(loginAttemptService.getRetryAfterSeconds(EMAIL))
                .thenReturn(600L);

        mockUserFound();

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // 첫 번째 요청: 비밀번호 오류
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        // 두 번째 요청: 계정 일시 차단
        LoginAttemptBlockedException exception = assertThrows(
                LoginAttemptBlockedException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(
                LOGIN_BLOCK_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                600L,
                exception.getRetryAfterSeconds()
        );

        // 차단된 요청은 DB에 접근하지 않음
        verify(userRepository, times(1))
                .findByEmailForUpdate(EMAIL);

        verify(loginAttemptService, times(2))
                .isBlocked(EMAIL);

        verify(loginAttemptService)
                .getRetryAfterSeconds(EMAIL);

        // 실패 횟수 기록은 첫 요청에서만 실행
        verify(loginAttemptService, times(1))
                .recordFailure(EMAIL);

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        verifyNoInteractions(jwtTokenProvider);
    }
}
