package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.TemporaryPasswordRequest;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordControllerTest {

    @Mock
    private TemporaryPasswordService temporaryPasswordService;

    @InjectMocks
    private TemporaryPasswordController controller;

    // 1. 정상적인 임시 비밀번호 발급 요청
    @Test
    void shouldReturnAcceptedWhenRequested() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        ResponseEntity<Void> response =
                controller.issueTemporaryPassword(request);

        assertEquals(
                HttpStatus.ACCEPTED,
                response.getStatusCode()
        );

        assertNull(response.getBody());

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // 2. 요청한 이메일이 정확히 전달되는지 확인
    @Test
    void shouldPassCorrectEmailToService() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("user@example.com");

        controller.issueTemporaryPassword(request);

        verify(temporaryPasswordService)
                .issueTemporaryPassword("user@example.com");

        verifyNoMoreInteractions(temporaryPasswordService);
    }

    // 3. 이메일 발송 실패 시 예외 전파
    @Test
    void shouldPropagateMailFailure() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");

        assertThrows(
                MailSendException.class,
                () -> controller.issueTemporaryPassword(request)
        );

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }
}
