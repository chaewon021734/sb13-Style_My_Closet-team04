package com.project.sb13ootbsteam04.auth.scheduler;

import com.project.sb13ootbsteam04.auth.service.RefreshTokenCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jwt.refresh-token-cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenCleanupService refreshTokenCleanupService;

    // 이전 정리 작업 종료 후 지정된 시간이 지나면 다음 작업 실행
    @Scheduled(
            initialDelayString = "${jwt.refresh-token-cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${jwt.refresh-token-cleanup.fixed-delay-ms:3600000}"
    )
    public void cleanupExpiredTokens() {
        refreshTokenCleanupService.cleanupExpiredTokens();
    }
}
