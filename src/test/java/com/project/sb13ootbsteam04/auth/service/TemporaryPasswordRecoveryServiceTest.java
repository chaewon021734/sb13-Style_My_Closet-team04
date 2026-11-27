package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordRecoveryServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD_HASH = "encodedTemporaryPassword";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TemporaryPasswordRecoveryService recoveryService;

    private User user;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                EMAIL,
                "encodedOriginalPassword"
        );

        user.issueTemporaryPassword(
                PASSWORD_HASH,
                Instant.now().plusSeconds(180)
        );
    }

    // 1. SMTP 실패 후 정상 복구
    @Test
    void shouldClearTemporaryPasswordWhenStateMatches() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long issuedVersion = user.getLoginVersion();

        recoveryService.recover(
                EMAIL,
                PASSWORD_HASH,
                issuedVersion
        );

        assertNull(user.getTemporaryPasswordHash());
        assertNull(user.getTemporaryPasswordExpiresAt());

        assertEquals(
                issuedVersion + 1,
                user.getLoginVersion()
        );

        verify(userRepository).findByEmailForUpdate(EMAIL);
    }

    // 2. 사용자가 존재하지 않으면 종료
    @Test
    void shouldIgnoreUnknownUser() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                recoveryService.recover(
                        EMAIL,
                        PASSWORD_HASH,
                        user.getLoginVersion()
                )
        );
    }

    // 3. 로그인 버전이 변경되었다면 복구하지 않음
    @Test
    void shouldNotRecoverWhenLoginVersionChanged() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long issuedVersion = user.getLoginVersion();

        // 다른 로그인 발생
        user.increaseLoginVersion();

        recoveryService.recover(
                EMAIL,
                PASSWORD_HASH,
                issuedVersion
        );

        assertEquals(
                PASSWORD_HASH,
                user.getTemporaryPasswordHash()
        );

        assertNotNull(user.getTemporaryPasswordExpiresAt());

        assertEquals(
                issuedVersion + 1,
                user.getLoginVersion()
        );
    }

    // 4. 비밀번호 해시가 다르면 복구하지 않음
    @Test
    void shouldNotRecoverWhenPasswordHashChanged() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long issuedVersion = user.getLoginVersion();

        // 다른 발급 요청이 임시 비밀번호를 교체한 상황
        user.issueTemporaryPassword(
                "anotherEncodedPassword",
                Instant.now().plusSeconds(180)
        );

        // 버전은 새 발급 상태로 맞추되 이전 해시를 전달
        recoveryService.recover(
                EMAIL,
                PASSWORD_HASH,
                user.getLoginVersion()
        );

        assertEquals(
                "anotherEncodedPassword",
                user.getTemporaryPasswordHash()
        );

        assertNotNull(user.getTemporaryPasswordExpiresAt());
    }

    // 5. 예상 비밀번호 해시가 null이면 복구하지 않음
    @Test
    void shouldNotRecoverWhenExpectedHashIsNull() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long issuedVersion = user.getLoginVersion();

        recoveryService.recover(
                EMAIL,
                null,
                issuedVersion
        );

        assertEquals(
                PASSWORD_HASH,
                user.getTemporaryPasswordHash()
        );

        assertEquals(
                issuedVersion,
                user.getLoginVersion()
        );
    }
}
