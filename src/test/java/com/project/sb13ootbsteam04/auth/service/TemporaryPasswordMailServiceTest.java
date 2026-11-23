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

    private static final String SENDER =
            "otboo@example.com";

    private static final String RECEIVER =
            "user@example.com";

    private static final String TEMPORARY_PASSWORD =
            "Temp1234!abc";

    @BeforeEach
    void setUp() {

        // @Value 필드는 Mockito가 자동 주입하지 않으므로 직접 설정
        ReflectionTestUtils.setField(
                mailService,
                "senderEmail",
                SENDER
        );
    }

    // ========================================
    // 1. 임시 비밀번호 이메일 발송 성공
    // ========================================

    @Test
    void shouldSendTemporaryPasswordEmail() {

        // when
        mailService.sendTemporaryPassword(
                RECEIVER,
                TEMPORARY_PASSWORD
        );

        // then
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();

        assertArrayEquals(
                new String[]{RECEIVER},
                sentMessage.getTo()
        );

        assertEquals(
                SENDER,
                sentMessage.getFrom()
        );

        assertEquals(
                "[Otboo] 임시 비밀번호 안내",
                sentMessage.getSubject()
        );

        assertNotNull(sentMessage.getText());

        assertTrue(
                sentMessage.getText().contains(TEMPORARY_PASSWORD)
        );
    }

    // ========================================
    // 2. 이메일 본문에 3분 만료 안내 포함
    // ========================================

    @Test
    void shouldIncludeExpirationNotice() {

        // when
        mailService.sendTemporaryPassword(
                RECEIVER,
                TEMPORARY_PASSWORD
        );

        // then
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(mailSender).send(captor.capture());

        String body = captor.getValue().getText();

        assertNotNull(body);

        assertTrue(
                body.contains("3분")
        );

        assertTrue(
                body.contains("새로운 비밀번호로 변경")
        );
    }

    // ========================================
    // 3. SMTP 발송 실패 시 예외 전달
    // ========================================

    @Test
    void shouldPropagateMailSendException() {

        // given
        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        // when
        MailSendException exception = assertThrows(
                MailSendException.class,
                () -> mailService.sendTemporaryPassword(
                        RECEIVER,
                        TEMPORARY_PASSWORD
                )
        );

        // then
        assertEquals(
                "SMTP 발송 실패",
                exception.getMessage()
        );

        verify(mailSender).send(any(SimpleMailMessage.class));
    }
}
