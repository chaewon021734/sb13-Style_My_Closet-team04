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

    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final String ENCODED_PASSWORD = "encodedNewPassword";

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

        userId = UUID.randomUUID();

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedOldPassword"
        );

        ReflectionTestUtils.setField(user, "id", userId);
    }

    // 1. 정상적인 비밀번호 재설정
    @Test
    void shouldResetPasswordSuccessfully() {

        givenActiveTemporaryPassword();

        long previousVersion = user.getLoginVersion();

        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(userId, NEW_PASSWORD, user.getLoginVersion());

        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertNull(user.getTemporaryPasswordHash());
        assertNull(user.getTemporaryPasswordExpiresAt());

        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );

        verify(passwordEncoder).encode(NEW_PASSWORD);
    }

    // 2. 존재하지 않는 사용자
    @Test
    void shouldRejectUnknownUser() {

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        NEW_PASSWORD, user.getLoginVersion()
                )
        );

        verifyNoInteractions(passwordEncoder);
    }

    // 3. 잠긴 계정
    @Test
    void shouldRejectLockedAccount() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        user.changeLocked(true);

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        long previousVersion = user.getLoginVersion();

        assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        NEW_PASSWORD, user.getLoginVersion()
                )
        );

        assertEquals(previousVersion, user.getLoginVersion());
        verifyNoInteractions(passwordEncoder);
    }

    // 4. 만료된 임시 비밀번호
    @Test
    void shouldRejectExpiredTemporaryPassword() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().minusSeconds(60)
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        NEW_PASSWORD, user.getLoginVersion()
                )
        );

        verifyNoInteractions(passwordEncoder);
    }

    // 5. 임시 비밀번호가 없는 사용자
    @Test
    void shouldRejectWithoutTemporaryPassword() {

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));

        assertThrows(
                BadCredentialsException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        NEW_PASSWORD, user.getLoginVersion()
                )
        );

        verifyNoInteractions(passwordEncoder);
    }

    // 6. 빈 비밀번호 거부
    @Test
    void shouldRejectBlankPassword() {

        givenActiveTemporaryPassword();

        assertThrows(
                IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(
                        userId,
                        " ", user.getLoginVersion()
                )
        );

        verifyNoInteractions(passwordEncoder);
    }

    // 7. 비밀번호 재설정 후 임시 비밀번호 사용 종료
    @Test
    void shouldInvalidateTemporaryPasswordAfterReset() {

        givenActiveTemporaryPassword();

        when(passwordEncoder.encode(NEW_PASSWORD))
                .thenReturn(ENCODED_PASSWORD);

        passwordResetService.resetPassword(userId, NEW_PASSWORD, user.getLoginVersion());

        assertFalse(
                user.isTemporaryPasswordActive(Instant.now())
        );

        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertNull(user.getTemporaryPasswordHash());
        assertNull(user.getTemporaryPasswordExpiresAt());
    }

    @Test
    void shouldRejectMismatchedLoginVersion() {
        givenActiveTemporaryPassword();
        long version = user.getLoginVersion();
        assertThrows(BadCredentialsException.class,
                () -> passwordResetService.resetPassword(userId, NEW_PASSWORD, version + 1));
        assertEquals(version, user.getLoginVersion());
        assertEquals("encodedOldPassword", user.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectOldVersionAfterTemporaryPasswordReissue() {
        givenActiveTemporaryPassword();
        long oldVersion = user.getLoginVersion();
        user.issueTemporaryPassword("newTemporaryHash", Instant.now().plusSeconds(180));
        assertThrows(BadCredentialsException.class,
                () -> passwordResetService.resetPassword(userId, NEW_PASSWORD, oldVersion));
        assertEquals("newTemporaryHash", user.getTemporaryPasswordHash());
        assertEquals("encodedOldPassword", user.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldRejectReusingVersionAfterSuccessfulReset() {
        givenActiveTemporaryPassword();
        long version = user.getLoginVersion();
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
        passwordResetService.resetPassword(userId, NEW_PASSWORD, version);
        assertThrows(BadCredentialsException.class,
                () -> passwordResetService.resetPassword(userId, "AnotherPassword123!", version));
        assertEquals(ENCODED_PASSWORD, user.getPassword());
        assertEquals(version + 1, user.getLoginVersion());
        verify(passwordEncoder, times(1)).encode(anyString());
    }

    private void givenActiveTemporaryPassword() {

        user.issueTemporaryPassword(
                "encodedTemporaryPassword",
                Instant.now().plusSeconds(180)
        );

        when(userRepository.findByIdForUpdate(userId))
                .thenReturn(Optional.of(user));
    }
}
