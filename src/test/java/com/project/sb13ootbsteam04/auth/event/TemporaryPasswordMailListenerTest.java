package com.project.sb13ootbsteam04.auth.event;

import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordMailService;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordRecoveryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordMailListenerTest {

    private static final String EMAIL = "test@example.com";
    private static final String TEMP_PASSWORD = "TempPassword123!";
    private static final String PASSWORD_HASH = "encodedTemporaryPassword";
    private static final long LOGIN_VERSION = 2L;

    @Mock
    private TemporaryPasswordMailService mailService;

    @Mock
    private TemporaryPasswordRecoveryService recoveryService;

    @InjectMocks
    private TemporaryPasswordMailListener listener;

    private TemporaryPasswordIssuedEvent event;

    @BeforeEach
    void setUp() {
        event = new TemporaryPasswordIssuedEvent(
                EMAIL,
                TEMP_PASSWORD,
                PASSWORD_HASH,
                LOGIN_VERSION
        );
    }

    // 1. 이메일 발송 성공 시 복구하지 않음
    @Test
    void shouldNotRecoverWhenMailSucceeds() {

        listener.handle(event);

        verify(mailService, times(1))
                .sendTemporaryPassword(EMAIL, TEMP_PASSWORD);

        verifyNoInteractions(recoveryService);
    }

    // 2. SMTP 오류 시 복구 서비스 호출
    @Test
    void shouldRecoverWhenMailFails() {

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailService)
                .sendTemporaryPassword(EMAIL, TEMP_PASSWORD);

        assertDoesNotThrow(() -> listener.handle(event));

        verify(recoveryService).recover(
                EMAIL,
                PASSWORD_HASH,
                LOGIN_VERSION
        );
    }

    // 3. 복구 서비스에서 오류 발생 시 예외 처리
    @Test
    void shouldHandleRecoveryFailure() {

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailService)
                .sendTemporaryPassword(EMAIL, TEMP_PASSWORD);

        doThrow(new IllegalStateException("복구 실패"))
                .when(recoveryService)
                .recover(EMAIL, PASSWORD_HASH, LOGIN_VERSION);

        assertDoesNotThrow(() -> listener.handle(event));

        verify(mailService).sendTemporaryPassword(
                EMAIL,
                TEMP_PASSWORD
        );

        verify(recoveryService).recover(
                EMAIL,
                PASSWORD_HASH,
                LOGIN_VERSION
        );
    }

    // 4. 다른 이벤트의 데이터도 정확하게 전달
    @Test
    void shouldPassCorrectEventData() {

        TemporaryPasswordIssuedEvent anotherEvent =
                new TemporaryPasswordIssuedEvent(
                        "another@example.com",
                        "AnotherTemp123!",
                        "anotherEncodedPassword",
                        5L
                );

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailService)
                .sendTemporaryPassword(
                        "another@example.com",
                        "AnotherTemp123!"
                );

        listener.handle(anotherEvent);

        verify(recoveryService).recover(
                "another@example.com",
                "anotherEncodedPassword",
                5L
        );
    }
}
