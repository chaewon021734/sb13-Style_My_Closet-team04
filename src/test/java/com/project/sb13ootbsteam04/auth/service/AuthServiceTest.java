package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.dto.LoginRequest;
import com.project.sb13ootbsteam04.auth.dto.LoginResponse;
import com.project.sb13ootbsteam04.auth.dto.LoginResult;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String RAW_REFRESH_TOKEN =
            "a".repeat(43);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User user;
    private LoginRequest loginRequest;

    private RefreshTokenService.IssuedRefreshToken issuedRefreshToken;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedPassword"
        );

        ReflectionTestUtils.setField(user, "id", USER_ID);

        loginRequest = new LoginRequest(
                "test@example.com",
                "password123"
        );

        issuedRefreshToken =
                new RefreshTokenService.IssuedRefreshToken(
                        USER_ID,
                        RAW_REFRESH_TOKEN,
                        Instant.now().plusSeconds(604800)
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

        when(refreshTokenService.issue(user))
                .thenReturn(issuedRefreshToken);

        long previousVersion = user.getLoginVersion();

        LoginResult result = authService.login(loginRequest);

        LoginResponse response = result.response();

        assertEquals("test.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(previousVersion + 1, user.getLoginVersion());

        // 사용자 정보 검증
        assertNotNull(response.userDto());
        assertEquals(USER_ID, response.userDto().id());
        assertEquals("test@example.com", response.userDto().email());
        assertEquals("테스트사용자", response.userDto().name());
        assertEquals("USER", response.userDto().role());
        assertFalse(response.userDto().locked());

        // Refresh Token 발급 검증
        assertTrue(result.hasRefreshToken());
        assertNotNull(result.refreshToken());

        assertEquals(
                USER_ID,
                result.refreshToken().userId()
        );

        assertEquals(
                RAW_REFRESH_TOKEN,
                result.refreshToken().rawToken()
        );

        assertEquals(
                issuedRefreshToken.expiresAt(),
                result.refreshToken().expiresAt()
        );

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verify(jwtTokenProvider).generateAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateTemporaryAccessToken(any(User.class));

        verify(refreshTokenService).issue(user);
    }

    // 2. 존재하지 않는 이메일
    @Test
    void shouldRejectUnknownEmail() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.empty());

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService
        );
    }

    // 3. 잘못된 비밀번호
    @Test
    void shouldRejectWrongPassword() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(false);

        long previousVersion = user.getLoginVersion();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(previousVersion, user.getLoginVersion());

        verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        verifyNoInteractions(
                jwtTokenProvider,
                refreshTokenService
        );
    }

    // 4. 잠긴 계정 + 올바른 비밀번호
    @Test
    void shouldRejectLoginWhenAccountIsLocked() {

        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        long previousVersion = user.getLoginVersion();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(previousVersion, user.getLoginVersion());

        // 잠긴 계정은 비밀번호 비교 및 토큰 발급 없이 거부
        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService
        );
    }

    // 5. 잠긴 계정 + 잘못된 비밀번호
    @Test
    void shouldRejectLockedAccountWithWrongPassword() {

        ReflectionTestUtils.setField(user, "locked", true);

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        long previousVersion = user.getLoginVersion();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(previousVersion, user.getLoginVersion());

        verifyNoInteractions(
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService
        );
    }

    // 6. 로그인 성공마다 버전 증가
    @Test
    void shouldIncreaseLoginVersionOnEverySuccessfulLogin() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("test.jwt.token");

        when(refreshTokenService.issue(user))
                .thenReturn(issuedRefreshToken);

        LoginResult firstResult = authService.login(loginRequest);
        long firstVersion = user.getLoginVersion();

        LoginResult secondResult = authService.login(loginRequest);
        long secondVersion = user.getLoginVersion();

        assertEquals(1L, firstVersion);
        assertEquals(2L, secondVersion);

        assertTrue(firstResult.hasRefreshToken());
        assertTrue(secondResult.hasRefreshToken());

        verify(jwtTokenProvider, times(2))
                .generateAccessToken(user);

        verify(refreshTokenService, times(2))
                .issue(user);
    }

    // 7. JWT 및 Refresh Token 발급 전에 버전 증가
    @Test
    void shouldIncreaseLoginVersionBeforeIssuingJwt() {

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenAnswer(invocation -> {
                    assertEquals(1L, user.getLoginVersion());
                    return "test.jwt.token";
                });

        when(refreshTokenService.issue(user))
                .thenAnswer(invocation -> {
                    assertEquals(1L, user.getLoginVersion());
                    return issuedRefreshToken;
                });

        LoginResult result = authService.login(loginRequest);

        assertEquals(1L, user.getLoginVersion());
        assertTrue(result.hasRefreshToken());

        InOrder inOrder = inOrder(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                refreshTokenService
        );

        inOrder.verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        inOrder.verify(passwordEncoder)
                .matches("password123", "encodedPassword");

        inOrder.verify(jwtTokenProvider)
                .generateAccessToken(user);

        inOrder.verify(refreshTokenService)
                .issue(user);
    }

    // 8. 유효한 임시 비밀번호로 로그인
    @Test
    void shouldLoginWithTemporaryPassword() {

        user.issueTemporaryPassword(
                "temporaryEncodedPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(false);

        when(passwordEncoder.matches(
                "password123", "temporaryEncodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateTemporaryAccessToken(user))
                .thenReturn("temporary.jwt.token");

        long previousVersion = user.getLoginVersion();

        LoginResult result = authService.login(loginRequest);

        LoginResponse response = result.response();

        assertEquals("temporary.jwt.token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(previousVersion + 1, user.getLoginVersion());

        // 임시 비밀번호 로그인 시 Refresh Token 발급 금지
        assertFalse(result.hasRefreshToken());
        assertNull(result.refreshToken());

        verify(jwtTokenProvider)
                .generateTemporaryAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateAccessToken(any(User.class));

        verifyNoInteractions(refreshTokenService);
    }

    // 9. 만료된 임시 비밀번호는 로그인 거부
    @Test
    void shouldRejectExpiredTemporaryPassword() {

        user.issueTemporaryPassword(
                "temporaryEncodedPassword",
                Instant.now().minusSeconds(60)
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(false);

        long previousVersion = user.getLoginVersion();

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.login(loginRequest)
        );

        assertEquals(LOGIN_FAILURE_MESSAGE, exception.getMessage());
        assertEquals(previousVersion, user.getLoginVersion());

        verify(passwordEncoder, never())
                .matches("password123", "temporaryEncodedPassword");

        verifyNoInteractions(
                jwtTokenProvider,
                refreshTokenService
        );
    }

    // 10. 일반 비밀번호가 일치하면 일반 JWT 우선 발급
    @Test
    void shouldPreferNormalPasswordWhenBothExist() {

        user.issueTemporaryPassword(
                "temporaryEncodedPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "password123", "encodedPassword"
        )).thenReturn(true);

        when(jwtTokenProvider.generateAccessToken(user))
                .thenReturn("normal.jwt.token");

        when(refreshTokenService.issue(user))
                .thenReturn(issuedRefreshToken);

        LoginResult result = authService.login(loginRequest);

        LoginResponse response = result.response();

        assertEquals("normal.jwt.token", response.accessToken());

        // 일반 비밀번호 인증 성공 시 Refresh Token 발급
        assertTrue(result.hasRefreshToken());
        assertEquals(issuedRefreshToken, result.refreshToken());

        verify(passwordEncoder, never())
                .matches("password123", "temporaryEncodedPassword");

        verify(jwtTokenProvider).generateAccessToken(user);

        verify(jwtTokenProvider, never())
                .generateTemporaryAccessToken(any(User.class));

        verify(refreshTokenService).issue(user);
    }
}