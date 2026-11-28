package com.project.sb13ootbsteam04.auth.service;

import com.project.sb13ootbsteam04.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    // 만료된 Refresh Token 일괄 삭제
    @Transactional
    public int cleanupExpiredTokens() {
        Instant now = Instant.now();

        int deletedCount = refreshTokenRepository
                .deleteByExpiresAtLessThanEqual(now);

        if (deletedCount > 0) {
            log.info("만료된 Refresh Token 정리: {}개 삭제 쿼리 실행", deletedCount);
        }

        return deletedCount;
    }
}
