package com.project.sb13ootbsteam04.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private TemporaryPasswordMailService mailService;

    @BeforeEach
    void setUp() {

        // 실제 SMTP 계정 대신 테스트용 주소 사용
        ReflectionTestUtils.setField(
                mailService,
                "senderEmail",
                "no-reply@example.com"
        );
    }

    // 1. 발신자와 수신자가 올바르게 설정되는지 확인
    @Test
    void shouldSetCorrectSenderAndReceiver() {

        mailService.sendTemporaryPassword(
                "user@example.com",
                "TempPass123!"
        );

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertArrayEquals(
                new String[]{"no-reply@example.com"},
                message.getFrom() == null
                        ? new String[]{}
                        : new String[]{message.getFrom()}
        );

        assertArrayEquals(
                new String[]{"user@example.com"},
                message.getTo()
        );
    }

    // 2. 이메일 제목 및 임시 비밀번호 포함 여부
    @Test
    void shouldIncludeTemporaryPasswordInMail() {

        mailService.sendTemporaryPassword(
                "user@example.com",
                "TempPass123!"
        );

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals(
                "[Otboo] 임시 비밀번호 안내",
                message.getSubject()
        );

        assertNotNull(message.getText());

        assertTrue(
                message.getText().contains("TempPass123!")
        );

        assertTrue(
                message.getText().contains("3분")
        );
    }

    // 3. 실제 이메일 발송 요청이 한 번만 이루어지는지 확인
    @Test
    void shouldSendMailExactlyOnce() {

        mailService.sendTemporaryPassword(
                "user@example.com",
                "TempPass123!"
        );

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }

    // 4. SMTP 오류 발생 시 예외가 전파되는지 확인
    @Test
    void shouldPropagateMailSendException() {

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        assertThrows(
                MailSendException.class,
                () -> mailService.sendTemporaryPassword(
                        "user@example.com",
                        "TempPass123!"
                )
        );

        verify(mailSender, times(1))
                .send(any(SimpleMailMessage.class));
    }
}
