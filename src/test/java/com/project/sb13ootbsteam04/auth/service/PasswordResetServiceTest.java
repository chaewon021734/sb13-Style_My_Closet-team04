package com.project.sb13ootbsteam04.auth.service;

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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedOriginalPassword"
        );

        userId = UUID.randomUUID();

        ReflectionTestUtils.setField(
                user,
                "id",
                userId
        );
    }

    // ========================================
    // 1. 정상 비밀번호 재설정
    // ========================================

    @Test
    void shouldResetPasswordSuccessfully() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123!"))
                .thenReturn("encodedNewPassword");

        // when
        passwordResetService.resetPassword(
                userId,
                "NewPassword123!"
        );

        // then
        assertEquals(
                "encodedNewPassword",
                user.getPassword()
        );

        verify(passwordEncoder)
                .encode("NewPassword123!");
    }

    // ========================================
    // 2. 비밀번호 변경 후 임시 비밀번호 삭제
    // ========================================

    @Test
    void shouldClearTemporaryPasswordAfterReset() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123!"))
                .thenReturn("encodedNewPassword");

        // when
        passwordResetService.resetPassword(
                userId,
                "NewPassword123!"
        );

        // then
        assertNull(user.getTemporaryPasswordHash());

        assertNull(user.getTemporaryPasswordExpiresAt());

        assertFalse(
                user.isTemporaryPasswordActive(Instant.now())
        );
    }

    // ========================================
    // 3. 비밀번호 변경 후 로그인 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionAfterReset() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        // 임시 비밀번호 로그인 성공 상황
        user.increaseLoginVersion();

        long previousVersion = user.getLoginVersion();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.encode("NewPassword123!"))
                .thenReturn("encodedNewPassword");

        // when
        passwordResetService.resetPassword(
                userId,
                "NewPassword123!"
        );

        // then
        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );
    }

    // ========================================
    // 4. 만료된 임시 비밀번호 재설정 차단
    // ========================================

    @Test
    void shouldRejectExpiredTemporaryPassword() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(1)
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        "NewPassword123!"
                )
        );

        // then
        assertEquals(
                "임시 비밀번호가 만료되었거나 유효하지 않습니다.",
                exception.getMessage()
        );

        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(passwordEncoder);
    }

    // ========================================
    // 5. 잠긴 계정의 비밀번호 재설정 차단
    // ========================================

    @Test
    void shouldRejectLockedAccount() {

        // given
        user.changeLocked(true);

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        "NewPassword123!"
                )
        );

        // then
        assertEquals(
                "비밀번호를 재설정할 수 없습니다.",
                exception.getMessage()
        );

        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(passwordEncoder);
    }

    // ========================================
    // 6. 존재하지 않는 사용자 재설정 차단
    // ========================================

    @Test
    void shouldRejectUnknownUser() {

        // given
        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when
        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        "NewPassword123!"
                )
        );

        // then
        assertEquals(
                "비밀번호를 재설정할 수 없습니다.",
                exception.getMessage()
        );

        verifyNoInteractions(passwordEncoder);
    }

    // ========================================
    // 7. 빈 비밀번호 재설정 차단
    // ========================================

    @Test
    void shouldRejectBlankNewPassword() {

        // given
        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        "   "
                )
        );

        // then
        assertEquals(
                "새 비밀번호는 필수입니다.",
                exception.getMessage()
        );

        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        verifyNoInteractions(passwordEncoder);
    }
}
