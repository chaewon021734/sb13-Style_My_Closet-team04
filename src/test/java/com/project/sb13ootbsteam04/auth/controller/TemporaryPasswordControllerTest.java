package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.TemporaryPasswordRequest;
import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;
import com.project.sb13ootbsteam04.auth.ratelimit.TemporaryPasswordRateLimiter;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
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

    private static final String IP_ADDRESS = "127.0.0.1";

    @Mock
    private TemporaryPasswordService temporaryPasswordService;

    @Mock
    private TemporaryPasswordRateLimiter rateLimiter;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private TemporaryPasswordController controller;

    @BeforeEach
    void setUp() {
        lenient().when(httpRequest.getRemoteAddr())
                .thenReturn(IP_ADDRESS);
    }

    // 1. 정상적인 임시 비밀번호 발급 요청
    @Test
    void shouldReturnAcceptedWhenRequested() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        ResponseEntity<Void> response =
                controller.issueTemporaryPassword(
                        request,
                        httpRequest
                );

        assertEquals(
                HttpStatus.ACCEPTED,
                response.getStatusCode()
        );

        assertNull(response.getBody());

        verify(rateLimiter).checkAndRecord(
                "test@example.com",
                IP_ADDRESS
        );

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // 2. 요청한 이메일이 정확히 전달되는지 확인
    @Test
    void shouldPassCorrectEmailToService() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("user@example.com");

        controller.issueTemporaryPassword(
                request,
                httpRequest
        );

        verify(rateLimiter).checkAndRecord(
                "user@example.com",
                IP_ADDRESS
        );

        verify(temporaryPasswordService)
                .issueTemporaryPassword("user@example.com");

        verifyNoMoreInteractions(temporaryPasswordService);
    }

    // 3. 기존 서비스 오류는 그대로 전파
    @Test
    void shouldPropagateMailFailure() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        doThrow(new MailSendException("SMTP 발송 실패"))
                .when(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");

        assertThrows(
                MailSendException.class,
                () -> controller.issueTemporaryPassword(
                        request,
                        httpRequest
                )
        );

        verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }

    // 4. 이메일과 IP가 Rate Limiter에 정확히 전달되는지 확인
    @Test
    void shouldPassEmailAndIpToRateLimiter() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("another@example.com");

        controller.issueTemporaryPassword(
                request,
                httpRequest
        );

        verify(rateLimiter).checkAndRecord(
                "another@example.com",
                IP_ADDRESS
        );
    }

    // 5. 요청 횟수 초과 시 예외 발생
    @Test
    void shouldThrowWhenRateLimitExceeded() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        doThrow(new TemporaryPasswordRateLimitException())
                .when(rateLimiter)
                .checkAndRecord("test@example.com", IP_ADDRESS);

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> controller.issueTemporaryPassword(
                        request,
                        httpRequest
                )
        );

        verify(temporaryPasswordService, never())
                .issueTemporaryPassword(anyString());
    }

    // 6. 요청 제한을 통과해야 발급 서비스가 실행됨
    @Test
    void shouldCheckRateLimitBeforeIssuingPassword() {

        TemporaryPasswordRequest request =
                new TemporaryPasswordRequest("test@example.com");

        controller.issueTemporaryPassword(
                request,
                httpRequest
        );

        var inOrder = inOrder(
                rateLimiter,
                temporaryPasswordService
        );

        inOrder.verify(rateLimiter).checkAndRecord(
                "test@example.com",
                IP_ADDRESS
        );

        inOrder.verify(temporaryPasswordService)
                .issueTemporaryPassword("test@example.com");
    }
}
