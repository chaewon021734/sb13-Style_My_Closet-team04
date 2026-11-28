package com.project.sb13ootbsteam04.auth.scheduler;

import com.project.sb13ootbsteam04.auth.service.RefreshTokenCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenCleanupService cleanupService;

    @InjectMocks
    private RefreshTokenCleanupScheduler scheduler;

    @Test
    void shouldDelegateCleanupOnce() {
        scheduler.cleanupExpiredTokens();
        verify(cleanupService).cleanupExpiredTokens();
        verifyNoMoreInteractions(cleanupService);
    }

    @Test
    void shouldDelegateEveryInvocation() {
        scheduler.cleanupExpiredTokens();
        scheduler.cleanupExpiredTokens();
        scheduler.cleanupExpiredTokens();
        verify(cleanupService, times(3)).cleanupExpiredTokens();
        verifyNoMoreInteractions(cleanupService);
    }

    @Test
    void shouldPropagateCleanupFailure() {
        IllegalStateException failure = new IllegalStateException("정리 실패");
        when(cleanupService.cleanupExpiredTokens()).thenThrow(failure);
        assertThatThrownBy(scheduler::cleanupExpiredTokens).isSameAs(failure);
        verify(cleanupService).cleanupExpiredTokens();
    }
}
