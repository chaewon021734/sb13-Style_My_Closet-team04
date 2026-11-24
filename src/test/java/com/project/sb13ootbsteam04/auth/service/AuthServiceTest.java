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

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    // 로그인 실패 횟수 관리 Service 추가
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

        // given
        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // when
        LoginResponse response =
                authService.login(loginRequest);

        // then
        assertEquals(
                "test.jwt.token",
                response.accessToken()
        );

        assertEquals(
                "Bearer",
                response.tokenType()
        );

        assertEquals(
                1L,
                user.getLoginVersion()
        );

        verify(userRepository)
                .findByEmailForUpdate(EMAIL);

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider)
                .generateAccessToken(user);

        // 로그인 성공 시 실패 기록 초기화
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

        // given
        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
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

        // given
        mockUserFound();

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
        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                0L,
                user.getLoginVersion()
        );

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        // 로그인 실패 횟수 기록
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

        // given
        user.changeLocked(true);
        mockUserFound();

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                0L,
                user.getLoginVersion()
        );

        // 관리자 잠금 계정은 비밀번호 검증 전 차단
        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider
        );

        // 관리자 잠금은 로그인 실패 횟수와 별개
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

        // given
        user.changeLocked(true);
        mockUserFound();

        LoginRequest wrongRequest = new LoginRequest(
                EMAIL,
                "wrongPassword"
        );

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(wrongRequest)
        );

        // then
        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                0L,
                user.getLoginVersion()
        );

        // 잠긴 계정은 비밀번호가 무엇이든 차단
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

        // given
        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // when
        authService.login(loginRequest);

        assertEquals(
                1L,
                user.getLoginVersion()
        );

        authService.login(loginRequest);

        // then
        assertEquals(
                2L,
                user.getLoginVersion()
        );

        verify(userRepository, times(2))
                .findByEmailForUpdate(EMAIL);

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);

        verify(loginAttemptService, times(2))
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 7. 로그인 버전 증가 후 일반 JWT 발급
    // ========================================

    @Test
    void shouldIncreaseVersionBeforeGeneratingToken() {

        // given
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

        // when
        LoginResponse response =
                authService.login(loginRequest);

        // then
        assertEquals(
                "test.jwt.token",
                response.accessToken()
        );

        assertEquals(
                1L,
                user.getLoginVersion()
        );

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

        // given
        String temporaryPassword =
                "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        assertEquals(
                1L,
                user.getLoginVersion()
        );

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

        // when
        LoginResponse response =
                authService.login(temporaryRequest);

        // then
        assertEquals(
                "temporary.jwt.token",
                response.accessToken()
        );

        assertEquals(
                "Bearer",
                response.tokenType()
        );

        // 발급 +1, 임시 로그인 +1
        assertEquals(
                2L,
                user.getLoginVersion()
        );

        // 기존 일반 비밀번호 유지
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

        // 임시 비밀번호 로그인 성공도 실패 기록 초기화
        verify(loginAttemptService)
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 9. 만료된 임시 비밀번호 로그인 차단
    // ========================================

    @Test
    void shouldRejectExpiredTemporaryPassword() {

        // given
        String temporaryPassword =
                "Temp1234!abc";

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

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
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

        // 임시 비밀번호 만료 시에도 실패 횟수 기록
        verify(loginAttemptService)
                .recordFailure(EMAIL);

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

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
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

        // given
        String temporaryPassword =
                "Temp1234!abc";

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

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(temporaryRequest)
        );

        // then
        assertEquals(
                LOGIN_FAILURE_MESSAGE,
                exception.getMessage()
        );

        assertEquals(
                versionBeforeLogin,
                user.getLoginVersion()
        );

        // 관리자 잠금 상태에서는 암호 검증 없이 차단
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

        // given
        String temporaryPassword =
                "Temp1234!abc";

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        assertEquals(
                1L,
                user.getLoginVersion()
        );

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

                    // 발급 +1, 로그인 +1
                    assertEquals(
                            2L,
                            tokenUser.getLoginVersion()
                    );

                    return "temporary.jwt.token";
                });

        // when
        LoginResponse response =
                authService.login(temporaryRequest);

        // then
        assertEquals(
                "temporary.jwt.token",
                response.accessToken()
        );

        assertEquals(
                2L,
                user.getLoginVersion()
        );

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(user);

        verify(loginAttemptService)
                .recordSuccess(EMAIL);
    }

    // ========================================
    // 13. 계정이 차단되면 로그인 요청 거부
    // ========================================

    @Test
    void shouldRejectLoginWhenTemporarilyBlocked() {

        // given
        when(loginAttemptService.isBlocked(EMAIL))
                .thenReturn(true);

        // when
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getStatusCode()
        );

        // 계정 조회 및 비밀번호 검사 전에 차단
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

        // given
        mockUserFound();

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // when
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        // then
        verify(loginAttemptService)
                .isBlocked(EMAIL);

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        verify(loginAttemptService, never())
                .recordSuccess(anyString());

        assertEquals(
                0L,
                user.getLoginVersion()
        );

        verifyNoInteractions(jwtTokenProvider);
    }

    // ========================================
    // 15. 이전 실패 기록이 있어도 정상 로그인 시 초기화
    // ========================================

    @Test
    void shouldClearFailureHistoryAfterSuccessfulLogin() {

        // given
        mockUserFound();
        mockNormalPasswordSuccess();

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        // when
        LoginResponse response =
                authService.login(loginRequest);

        // then
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
    // 16. 5회 실패 후 다음 로그인 시도 차단
    // ========================================

    @Test
    void shouldRejectLoginAfterFailureLimitReached() {

        // given
        when(loginAttemptService.isBlocked(EMAIL))
                .thenReturn(false, true);

        mockUserFound();

        when(passwordEncoder.matches(
                "password123",
                "encodedPassword"
        )).thenReturn(false);

        // 1번째 실패
        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        verify(loginAttemptService)
                .recordFailure(EMAIL);

        // 다음 요청에서는 이미 차단된 상태라고 가정
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(loginRequest)
        );

        // then
        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getStatusCode()
        );

        // 차단된 요청에서는 추가 사용자 조회 없음
        verify(userRepository, times(1))
                .findByEmailForUpdate(EMAIL);

        verify(loginAttemptService, times(2))
                .isBlocked(EMAIL);

        verify(loginAttemptService, times(1))
                .recordFailure(EMAIL);

        verifyNoInteractions(jwtTokenProvider);
    }
}
