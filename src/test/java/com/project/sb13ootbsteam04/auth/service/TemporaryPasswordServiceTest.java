package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailSendException;
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

    @Mock
    private TemporaryPasswordMailService mailService;

    @InjectMocks
    private TemporaryPasswordService temporaryPasswordService;

    private User user;

    private static final String EMAIL = "test@example.com";
    private static final String TEMP_PASSWORD = "Temp1234!abc";
    private static final String ENCODED_PASSWORD =
            "encodedTemporaryPassword";

    @BeforeEach
    void setUp() {
        user = new User(
                "테스트사용자",
                EMAIL,
                "encodedOriginalPassword"
        );
    }

    // ========================================
    // 공통 Mock 설정
    // ========================================

    private void mockSuccessfulIssue() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn(TEMP_PASSWORD);

        when(passwordEncoder.encode(TEMP_PASSWORD))
                .thenReturn(ENCODED_PASSWORD);
    }

    // ========================================
    // 1. 정상 발급 및 이메일 전송
    // ========================================

    @Test
    void shouldIssueTemporaryPasswordAndSendEmail() {

        // given
        mockSuccessfulIssue();

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        // then
        assertEquals(
                ENCODED_PASSWORD,
                user.getTemporaryPasswordHash()
        );

        // 기존 비밀번호는 유지
        assertEquals(
                "encodedOriginalPassword",
                user.getPassword()
        );

        // 최초 발급 시 로그인 버전 증가
        assertEquals(1L, user.getLoginVersion());

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );
    }

    // ========================================
    // 2. 임시 비밀번호 3분 만료 시간 확인
    // ========================================

    @Test
    void shouldExpireTemporaryPasswordAfterThreeMinutes() {

        // given
        mockSuccessfulIssue();

        Instant before = Instant.now();

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        Instant after = Instant.now();

        // then
        Instant expiresAt =
                user.getTemporaryPasswordExpiresAt();

        assertNotNull(expiresAt);

        assertFalse(
                expiresAt.isBefore(before.plusSeconds(180))
        );

        assertFalse(
                expiresAt.isAfter(after.plusSeconds(180))
        );

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );
    }

    // ========================================
    // 3. 존재하지 않는 사용자에게 발급하지 않음
    // ========================================

    @Test
    void shouldNotIssuePasswordForUnknownEmail() {

        // given
        when(userRepository.findByEmailForUpdate("unknown@example.com"))
                .thenReturn(Optional.empty());

        // when
        assertDoesNotThrow(() ->
                temporaryPasswordService.issueTemporaryPassword(
                        "unknown@example.com"
                )
        );

        // then
        verifyNoInteractions(
                passwordGenerator,
                passwordEncoder,
                mailService
        );
    }

    // ========================================
    // 4. 잠긴 계정에 발급하지 않음
    // ========================================

    @Test
    void shouldNotIssuePasswordForLockedAccount() {

        // given
        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        // when
        assertDoesNotThrow(() ->
                temporaryPasswordService.issueTemporaryPassword(EMAIL)
        );

        // then
        assertNull(user.getTemporaryPasswordHash());

        assertEquals(0L, user.getLoginVersion());

        verifyNoInteractions(
                passwordGenerator,
                passwordEncoder,
                mailService
        );
    }

    // ========================================
    // 5. 재발급 시 이전 비밀번호 교체 및 JWT 무효화
    // ========================================

    @Test
    void shouldReplacePreviousPasswordAndIncreaseVersion() {

        // given
        user.issueTemporaryPassword(
                "previousHash",
                Instant.now().plusSeconds(60)
        );

        assertEquals(1L, user.getLoginVersion());

        mockSuccessfulIssue();

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        // then
        assertEquals(
                ENCODED_PASSWORD,
                user.getTemporaryPasswordHash()
        );

        // 재발급 시 버전 추가 증가
        assertEquals(2L, user.getLoginVersion());

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );
    }

    // ========================================
    // 6. 이메일 발송 실패 시 예외 전달
    // ========================================

    @Test
    void shouldPropagateMailSendException() {

        // given
        mockSuccessfulIssue();

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailService)
                .sendTemporaryPassword(EMAIL, TEMP_PASSWORD);

        // when
        MailSendException exception = assertThrows(
                MailSendException.class,
                () -> temporaryPasswordService
                        .issueTemporaryPassword(EMAIL)
        );

        // then
        assertEquals(
                "SMTP 발송 실패",
                exception.getMessage()
        );

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );
    }
}
