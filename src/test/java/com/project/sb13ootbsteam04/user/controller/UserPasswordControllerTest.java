package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.auth.service.PasswordResetService;
import com.project.sb13ootbsteam04.user.dto.request.UserPasswordUpdateRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPasswordControllerTest {

    private static final String NEW_PASSWORD = "NewPassword123!";
    private static final long LOGIN_VERSION = 5L;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private UserPasswordController controller;

    private UUID userId;
    private UserPasswordUpdateRequest request;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        request = new UserPasswordUpdateRequest(NEW_PASSWORD);
    }

    @Test
    void shouldUpdatePasswordSuccessfully() {

        givenTemporaryJwt(LOGIN_VERSION);

        ResponseEntity<Void> response =
                controller.updatePassword(userId, jwt, request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());

        verify(passwordResetService)
                .resetPassword(userId, NEW_PASSWORD, LOGIN_VERSION);
    }

    @Test
    void shouldRejectDifferentUserId() {

        when(jwt.getClaimAsString("authType"))
                .thenReturn("TEMPORARY");

        when(jwt.getSubject())
                .thenReturn(UUID.randomUUID().toString());

        assertForbidden(jwt);
    }

    @Test
    void shouldRejectNormalUserJwt() {

        when(jwt.getClaimAsString("authType"))
                .thenReturn("NORMAL");

        assertForbidden(jwt);
    }

    @Test
    void shouldRejectNormalAdminJwt() {

        Jwt adminJwt = Jwt.withTokenValue("admin-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("authType", "NORMAL")
                .claim("role", "ADMIN")
                .claim("loginVersion", LOGIN_VERSION)
                .build();

        assertForbidden(adminJwt);
    }

    @Test
    void shouldRejectMissingJwt() {

        assertForbidden(null);
    }

    @Test
    void shouldRejectMissingAuthType() {

        when(jwt.getClaimAsString("authType"))
                .thenReturn(null);

        assertForbidden(jwt);
    }

    @Test
    void shouldRejectMissingLoginVersion() {

        givenTemporaryJwt(null);

        assertInvalidLoginVersion();
    }

    @Test
    void shouldRejectStringLoginVersion() {

        givenTemporaryJwt("5");

        assertInvalidLoginVersion();
    }

    @Test
    void shouldRejectFractionalLoginVersion() {

        givenTemporaryJwt(5.5);

        assertInvalidLoginVersion();
    }

    @Test
    void shouldPassCorrectUserIdAndPassword() {

        givenTemporaryJwt(LOGIN_VERSION);

        controller.updatePassword(userId, jwt, request);

        verify(passwordResetService)
                .resetPassword(userId, NEW_PASSWORD, LOGIN_VERSION);

        verifyNoMoreInteractions(passwordResetService);
    }

    @Test
    void shouldPassCorrectLoginVersion() {

        givenTemporaryJwt(99L);

        controller.updatePassword(userId, jwt, request);

        verify(passwordResetService)
                .resetPassword(userId, NEW_PASSWORD, 99L);
    }

    @Test
    void shouldPropagateServiceFailure() {

        givenTemporaryJwt(LOGIN_VERSION);

        doThrow(new BadCredentialsException("비밀번호 재설정 실패"))
                .when(passwordResetService)
                .resetPassword(userId, NEW_PASSWORD, LOGIN_VERSION);

        assertThrows(
                BadCredentialsException.class,
                () -> controller.updatePassword(userId, jwt, request)
        );
    }

    private void givenTemporaryJwt(Object loginVersion) {

        when(jwt.getClaimAsString("authType"))
                .thenReturn("TEMPORARY");

        when(jwt.getSubject())
                .thenReturn(userId.toString());

        when(jwt.getClaim("loginVersion"))
                .thenReturn(loginVersion);
    }

    private void assertForbidden(Jwt token) {

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.updatePassword(userId, token, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());

        verifyNoInteractions(passwordResetService);
    }

    private void assertInvalidLoginVersion() {

        assertThrows(
                BadCredentialsException.class,
                () -> controller.updatePassword(userId, jwt, request)
        );

        verifyNoInteractions(passwordResetService);
    }
}