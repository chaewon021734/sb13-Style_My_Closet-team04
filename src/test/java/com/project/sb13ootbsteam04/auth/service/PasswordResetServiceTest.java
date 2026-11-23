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

        when(userRepository.findByIdForUpdate(userId))
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

        // 발급 시 +1, 재설정 시 +1
        assertEquals(2L, user.getLoginVersion());

        verify(userRepository).findByIdForUpdate(userId);

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

        when(userRepository.findByIdForUpdate(userId))
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

        assertEquals(
                "encodedNewPassword",
                user.getPassword()
        );

        verify(userRepository).findByIdForUpdate(userId);
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

        // 임시 비밀번호 발급 시 버전 1
        assertEquals(1L, user.getLoginVersion());

        // 임시 비밀번호 로그인 성공 상황
        user.increaseLoginVersion();

        // 로그인 후 버전 2
        long previousVersion = user.getLoginVersion();
        assertEquals(2L, previousVersion);

        when(userRepository.findByIdForUpdate(userId))
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

        // 발급 1 + 로그인 1 + 재설정 1
        assertEquals(3L, user.getLoginVersion());

        verify(userRepository).findByIdForUpdate(userId);
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

        // 발급 자체는 성공했으므로 버전은 1
        long versionBeforeReset = user.getLoginVersion();
        assertEquals(1L, versionBeforeReset);

        when(userRepository.findByIdForUpdate(userId))
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

        // 기존 비밀번호 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        // 재설정 실패 시 버전 추가 증가 없음
        assertEquals(
                versionBeforeReset,
                user.getLoginVersion()
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository).findByIdForUpdate(userId);
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

        long versionBeforeReset = user.getLoginVersion();
        assertEquals(1L, versionBeforeReset);

        when(userRepository.findByIdForUpdate(userId))
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

        // 기존 비밀번호 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        // 재설정 실패로 인한 추가 버전 증가 없음
        assertEquals(
                versionBeforeReset,
                user.getLoginVersion()
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository).findByIdForUpdate(userId);
    }

    // ========================================
    // 6. 존재하지 않는 사용자 재설정 차단
    // ========================================

    @Test
    void shouldRejectUnknownUser() {

        // given
        when(userRepository.findByIdForUpdate(userId))
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

        verify(userRepository).findByIdForUpdate(userId);
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

        long versionBeforeReset = user.getLoginVersion();

        when(userRepository.findByIdForUpdate(userId))
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

        // 기존 비밀번호 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        // 실패했으므로 버전 변경 없음
        assertEquals(
                versionBeforeReset,
                user.getLoginVersion()
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository).findByIdForUpdate(userId);
    }
}
