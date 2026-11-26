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

        verify(passwordGenerator).generate();
        verify(passwordEncoder).encode(TEMP_PASSWORD);

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

        // 발급 직전 시간 + 180초보다 이전이면 안 됨
        assertFalse(
                expiresAt.isBefore(before.plusSeconds(180))
        );

        // 발급 직후 시간 + 180초보다 이후이면 안 됨
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
    // 5. 만료된 임시 비밀번호 재발급 및 JWT 무효화
    // ========================================

    @Test
    void shouldReplacePreviousPasswordAndIncreaseVersion() {

        // given
        // 기존 임시 비밀번호를 만료된 상태로 설정
        user.issueTemporaryPassword(
                "previousHash",
                Instant.now().minusSeconds(10)
        );

        assertEquals(1L, user.getLoginVersion());

        mockSuccessfulIssue();

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        // then
        // 기존 임시 비밀번호가 새 비밀번호로 변경됨
        assertEquals(
                ENCODED_PASSWORD,
                user.getTemporaryPasswordHash()
        );

        // 재발급 시 loginVersion 추가 증가
        assertEquals(2L, user.getLoginVersion());

        // 새로운 만료 시간이 설정되었는지 확인
        assertTrue(
                user.getTemporaryPasswordExpiresAt()
                        .isAfter(Instant.now())
        );

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

    // ========================================
    // 7. 기존 임시 비밀번호가 유효하면 재발급 차단
    // ========================================

    @Test
    void shouldNotReissueWhileTemporaryPasswordIsActive() {

        // given
        // 기존 임시 비밀번호의 유효기간이 2분 남은 상황
        user.issueTemporaryPassword(
                "previousHash",
                Instant.now().plusSeconds(120)
        );

        long previousLoginVersion = user.getLoginVersion();

        Instant previousExpiresAt =
                user.getTemporaryPasswordExpiresAt();

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        // then
        // 기존 임시 비밀번호 유지
        assertEquals(
                "previousHash",
                user.getTemporaryPasswordHash()
        );

        // 기존 만료 시간 유지
        assertEquals(
                previousExpiresAt,
                user.getTemporaryPasswordExpiresAt()
        );

        // loginVersion 증가하지 않음
        assertEquals(
                previousLoginVersion,
                user.getLoginVersion()
        );

        // 새 비밀번호 생성 및 암호화 없음
        // 이메일도 다시 발송하지 않음
        verifyNoInteractions(
                passwordGenerator,
                passwordEncoder,
                mailService
        );
    }

    // ========================================
    // 8. 임시 비밀번호 만료 후 재발급 허용
    // ========================================

    @Test
    void shouldReissueAfterTemporaryPasswordExpires() {

        // given
        user.issueTemporaryPassword(
                "expiredHash",
                Instant.now().minusSeconds(10)
        );

        long previousLoginVersion = user.getLoginVersion();

        mockSuccessfulIssue();

        // when
        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        // then
        // 새 임시 비밀번호로 교체
        assertEquals(
                ENCODED_PASSWORD,
                user.getTemporaryPasswordHash()
        );

        // loginVersion 증가
        assertEquals(
                previousLoginVersion + 1,
                user.getLoginVersion()
        );

        // 새 임시 비밀번호가 유효한 상태인지 확인
        assertTrue(
                user.isTemporaryPasswordActive(Instant.now())
        );

        // 새 비밀번호 생성 확인
        verify(passwordGenerator).generate();

        // 암호화 확인
        verify(passwordEncoder).encode(TEMP_PASSWORD);

        // 이메일 발송 확인
        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );
    }
}
