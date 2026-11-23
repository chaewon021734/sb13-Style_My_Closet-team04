package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TemporaryPasswordGenerator passwordGenerator;

    @InjectMocks
    private TemporaryPasswordService temporaryPasswordService;

    private User user;

    @BeforeEach
    void setUp() {

        user = new User(
                "테스트사용자",
                "test@example.com",
                "encodedOriginalPassword"
        );
    }

    // ========================================
    // 1. 정상 사용자에게 임시 비밀번호 발급
    // ========================================

    @Test
    void shouldIssueTemporaryPassword() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn("Temp1234!abc");

        when(passwordEncoder.encode("Temp1234!abc"))
                .thenReturn("encodedTemporaryPassword");

        // when
        String result = temporaryPasswordService
                .issueTemporaryPassword("test@example.com");

        // then
        assertEquals("Temp1234!abc", result);

        assertEquals(
                "encodedTemporaryPassword",
                user.getTemporaryPasswordHash()
        );

        // 기존 비밀번호는 변경되지 않아야 함
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordGenerator).generate();

        verify(passwordEncoder)
                .encode("Temp1234!abc");
    }

    // ========================================
    // 2. 임시 비밀번호 만료 시간이 3분인지 확인
    // ========================================

    @Test
    void shouldExpireTemporaryPasswordAfterThreeMinutes() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn("Temp1234!abc");

        when(passwordEncoder.encode("Temp1234!abc"))
                .thenReturn("encodedTemporaryPassword");

        Instant before = Instant.now();

        // when
        temporaryPasswordService.issueTemporaryPassword(
                "test@example.com"
        );

        Instant after = Instant.now();

        // then
        Instant expiresAt =
                user.getTemporaryPasswordExpiresAt();

        assertNotNull(expiresAt);

        // 서비스 호출 시작 시각 + 180초 이상
        assertFalse(
                expiresAt.isBefore(before.plusSeconds(180))
        );

        // 서비스 호출 종료 시각 + 180초 이하
        assertFalse(
                expiresAt.isAfter(after.plusSeconds(180))
        );

        // 발급 직후에는 임시 비밀번호가 유효해야 함
        assertTrue(
                user.isTemporaryPasswordActive(Instant.now())
        );
    }

    // ========================================
    // 3. 존재하지 않는 사용자 발급 차단
    // ========================================

    @Test
    void shouldRejectUnknownUser() {

        // given
        when(userRepository.findByEmailForUpdate("unknown@example.com"))
                .thenReturn(Optional.empty());

        // when
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> temporaryPasswordService
                        .issueTemporaryPassword("unknown@example.com")
        );

        // then
        assertEquals(
                "존재하지 않는 사용자입니다.",
                exception.getMessage()
        );

        // 사용자가 없으므로 비밀번호 생성 및 암호화를 수행하지 않음
        verifyNoInteractions(
                passwordEncoder,
                passwordGenerator
        );

        verify(userRepository)
                .findByEmailForUpdate("unknown@example.com");
    }

    // ========================================
    // 4. 재발급 시 이전 임시 비밀번호 교체
    // ========================================

    @Test
    void shouldReplacePreviousTemporaryPassword() {

        // given
        user.issueTemporaryPassword(
                "previousHash",
                Instant.now().plusSeconds(60)
        );

        // 최초 발급으로 로그인 버전 1
        assertEquals(1L, user.getLoginVersion());

        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn("NewTemp123!");

        when(passwordEncoder.encode("NewTemp123!"))
                .thenReturn("newEncodedPassword");

        // when
        temporaryPasswordService.issueTemporaryPassword(
                "test@example.com"
        );

        // then
        assertEquals(
                "newEncodedPassword",
                user.getTemporaryPasswordHash()
        );

        assertNotNull(
                user.getTemporaryPasswordExpiresAt()
        );

        // 기존 비밀번호는 그대로 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        // 재발급 시 로그인 버전이 한 번 더 증가해야 함
        assertEquals(2L, user.getLoginVersion());

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordEncoder)
                .encode("NewTemp123!");
    }

    // ========================================
    // 5. 최초 발급 시 로그인 버전 증가
    // ========================================

    @Test
    void shouldIncreaseLoginVersionWhenTemporaryPasswordIssued() {

        // given
        when(userRepository.findByEmailForUpdate("test@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn("Temp1234!abc");

        when(passwordEncoder.encode("Temp1234!abc"))
                .thenReturn("encodedTemporaryPassword");

        long previousVersion = user.getLoginVersion();

        assertEquals(0L, previousVersion);

        // when
        temporaryPasswordService.issueTemporaryPassword(
                "test@example.com"
        );

        // then
        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );

        assertEquals(
                "encodedTemporaryPassword",
                user.getTemporaryPasswordHash()
        );

        assertNotNull(
                user.getTemporaryPasswordExpiresAt()
        );

        verify(userRepository)
                .findByEmailForUpdate("test@example.com");

        verify(passwordGenerator).generate();

        verify(passwordEncoder)
                .encode("Temp1234!abc");
    }
}
