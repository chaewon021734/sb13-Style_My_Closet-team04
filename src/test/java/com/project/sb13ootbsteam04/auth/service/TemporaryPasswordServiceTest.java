package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.event.TemporaryPasswordIssuedEvent;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemporaryPasswordServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String TEMP_PASSWORD = "TempPassword123!";
    private static final String ENCODED_PASSWORD = "encodedTempPassword";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TemporaryPasswordGenerator passwordGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TemporaryPasswordService temporaryPasswordService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(
                "테스트사용자",
                EMAIL,
                "encodedOriginalPassword"
        );
    }

    // 1. 정상 발급 시 이벤트 발행
    @Test
    void shouldPublishEventAfterIssuingTemporaryPassword() {

        givenValidUser();

        long previousVersion = user.getLoginVersion();

        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        assertEquals(
                ENCODED_PASSWORD,
                user.getTemporaryPasswordHash()
        );

        assertTrue(
                user.isTemporaryPasswordActive(Instant.now())
        );

        assertEquals(
                previousVersion + 1,
                user.getLoginVersion()
        );

        ArgumentCaptor<TemporaryPasswordIssuedEvent> captor =
                ArgumentCaptor.forClass(
                        TemporaryPasswordIssuedEvent.class
                );

        verify(eventPublisher).publishEvent(captor.capture());

        TemporaryPasswordIssuedEvent event = captor.getValue();

        assertEquals(EMAIL, event.email());
        assertEquals(TEMP_PASSWORD, event.temporaryPassword());

        verify(passwordGenerator).generate();
        verify(passwordEncoder).encode(TEMP_PASSWORD);
    }

    // 2. 등록되지 않은 이메일은 발급하지 않음
    @Test
    void shouldIgnoreUnknownEmail() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.empty());

        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        verifyNoInteractions(
                passwordEncoder,
                passwordGenerator,
                eventPublisher
        );
    }

    // 3. 잠긴 계정은 발급하지 않음
    @Test
    void shouldNotIssueForLockedAccount() {

        user.changeLocked(true);

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long previousVersion = user.getLoginVersion();

        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        assertEquals(
                previousVersion,
                user.getLoginVersion()
        );

        assertNull(user.getTemporaryPasswordHash());

        verifyNoInteractions(
                passwordEncoder,
                passwordGenerator,
                eventPublisher
        );
    }

    // 4. 이미 유효한 임시 비밀번호가 있으면 재발급하지 않음
    @Test
    void shouldNotReissueActiveTemporaryPassword() {

        user.issueTemporaryPassword(
                "existingEncodedPassword",
                Instant.now().plusSeconds(120)
        );

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        long previousVersion = user.getLoginVersion();

        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        assertEquals(
                previousVersion,
                user.getLoginVersion()
        );

        assertEquals(
                "existingEncodedPassword",
                user.getTemporaryPasswordHash()
        );

        verifyNoInteractions(
                passwordEncoder,
                passwordGenerator,
                eventPublisher
        );
    }

    // 5. 이벤트 발행 실패 시 예외 전파
    @Test
    void shouldPropagateEventPublisherFailure() {

        givenValidUser();

        doThrow(new IllegalStateException("이벤트 발행 실패"))
                .when(eventPublisher)
                .publishEvent(any(TemporaryPasswordIssuedEvent.class));

        assertThrows(
                IllegalStateException.class,
                () -> temporaryPasswordService
                        .issueTemporaryPassword(EMAIL)
        );

        verify(eventPublisher)
                .publishEvent(any(TemporaryPasswordIssuedEvent.class));
    }

    // 6. 만료 시간을 3분으로 설정
    @Test
    void shouldSetExpirationToThreeMinutes() {

        givenValidUser();

        Instant before = Instant.now();

        temporaryPasswordService.issueTemporaryPassword(EMAIL);

        Instant after = Instant.now();

        Instant expiresAt = user.getTemporaryPasswordExpiresAt();

        assertNotNull(expiresAt);

        assertFalse(
                expiresAt.isBefore(
                        before.plus(Duration.ofMinutes(3))
                )
        );

        assertFalse(
                expiresAt.isAfter(
                        after.plus(Duration.ofMinutes(3))
                )
        );
    }

    private void givenValidUser() {

        when(userRepository.findByEmailForUpdate(EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordGenerator.generate())
                .thenReturn(TEMP_PASSWORD);

        when(passwordEncoder.encode(TEMP_PASSWORD))
                .thenReturn(ENCODED_PASSWORD);
    }
}
