package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.PasswordResetRequest;
import com.project.sb13ootbsteam04.auth.service.PasswordResetService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetControllerTest {

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private PasswordResetController controller;

    // 1. 정상적인 비밀번호 재설정
    @Test
    void shouldResetPasswordSuccessfully() {

        UUID userId = UUID.randomUUID();

        when(jwt.getSubject())
                .thenReturn(userId.toString());

        PasswordResetRequest request =
                new PasswordResetRequest("NewPassword123!");

        ResponseEntity<Void> response =
                controller.resetPassword(jwt, request);

        assertEquals(
                HttpStatus.NO_CONTENT,
                response.getStatusCode()
        );

        assertNull(response.getBody());

        verify(passwordResetService).resetPassword(
                userId,
                "NewPassword123!"
        );
    }

    // 2. JWT에 포함된 사용자 ID 사용
    @Test
    void shouldUseUserIdFromJwt() {

        UUID authenticatedUserId = UUID.randomUUID();

        when(jwt.getSubject())
                .thenReturn(authenticatedUserId.toString());

        PasswordResetRequest request =
                new PasswordResetRequest("ChangedPassword123!");

        controller.resetPassword(jwt, request);

        verify(passwordResetService).resetPassword(
                authenticatedUserId,
                "ChangedPassword123!"
        );

        verifyNoMoreInteractions(passwordResetService);
    }

    // 3. 서비스 예외 발생 시 성공 응답을 반환하지 않음
    @Test
    void shouldPropagatePasswordResetFailure() {

        UUID userId = UUID.randomUUID();

        when(jwt.getSubject())
                .thenReturn(userId.toString());

        PasswordResetRequest request =
                new PasswordResetRequest("NewPassword123!");

        doThrow(new IllegalStateException("비밀번호 변경 실패"))
                .when(passwordResetService)
                .resetPassword(userId, "NewPassword123!");

        assertThrows(
                IllegalStateException.class,
                () -> controller.resetPassword(jwt, request)
        );
    }
}
