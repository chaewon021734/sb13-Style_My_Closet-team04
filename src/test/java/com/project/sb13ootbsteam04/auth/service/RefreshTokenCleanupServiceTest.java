package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenCleanupService refreshTokenCleanupService;

    // 1. 만료 토큰 1개 삭제 결과 반환
    @Test
    void shouldReturnOneWhenOneExpiredTokenIsDeleted() {
        when(refreshTokenRepository.deleteByExpiresAtLessThanEqual(any(Instant.class)))
                .thenReturn(1);

        assertThat(refreshTokenCleanupService.cleanupExpiredTokens()).isEqualTo(1);
    }

    // 2. 만료 토큰이 없는 경우 0 반환
    @Test
    void shouldReturnZeroWhenNoExpiredTokensExist() {
        when(refreshTokenRepository.deleteByExpiresAtLessThanEqual(any(Instant.class)))
                .thenReturn(0);

        assertThat(refreshTokenCleanupService.cleanupExpiredTokens()).isZero();
    }

    // 3. 여러 만료 토큰 삭제 결과 반환
    @Test
    void shouldReturnDeletedTokenCount() {
        when(refreshTokenRepository.deleteByExpiresAtLessThanEqual(any(Instant.class)))
                .thenReturn(100);

        assertThat(refreshTokenCleanupService.cleanupExpiredTokens()).isEqualTo(100);
    }

    // 4. 일괄 삭제 메서드는 정확히 한 번 호출
    @Test
    void shouldCallBulkDeleteExactlyOnce() {
        refreshTokenCleanupService.cleanupExpiredTokens();

        verify(refreshTokenRepository, times(1))
                .deleteByExpiresAtLessThanEqual(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenRepository);
    }

    // 5. 현재 시간을 만료 기준으로 전달
    @Test
    void shouldPassCurrentTimeAsExpirationCutoff() {
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        Instant before = Instant.now();

        refreshTokenCleanupService.cleanupExpiredTokens();

        Instant after = Instant.now();
        verify(refreshTokenRepository)
                .deleteByExpiresAtLessThanEqual(cutoffCaptor.capture());

        assertThat(cutoffCaptor.getValue())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // 6. DB 삭제 실패 시 예외를 삼키지 않고 전파
    @Test
    void shouldPropagateRepositoryException() {
        var failure = new DataAccessResourceFailureException("DB 삭제 강제 실패");
        when(refreshTokenRepository.deleteByExpiresAtLessThanEqual(any(Instant.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> refreshTokenCleanupService.cleanupExpiredTokens())
                .isSameAs(failure);

        verify(refreshTokenRepository, times(1))
                .deleteByExpiresAtLessThanEqual(any(Instant.class));
        verifyNoMoreInteractions(refreshTokenRepository);
    }
}
