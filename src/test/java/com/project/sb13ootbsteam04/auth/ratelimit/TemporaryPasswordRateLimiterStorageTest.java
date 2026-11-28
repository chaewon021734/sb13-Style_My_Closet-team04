package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.global.support.RedisTestContainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Redis에 저장되는 요청 기록이 제한 시간 뒤 사라지는지 검증
class TemporaryPasswordRateLimiterStorageTest {

    private static final String EMAIL = "user@example.com";
    private static final String IP = "192.168.0.1";

    private static final Instant INITIAL_TIME =
            Instant.parse("2026-01-01T00:00:00Z");

    private Clock clock;
    private StringRedisTemplate redisTemplate;
    private TemporaryPasswordRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);

        when(clock.instant()).thenReturn(INITIAL_TIME);

        redisTemplate = RedisTestContainer.template();
        rateLimiter = new TemporaryPasswordRateLimiter(redisTemplate, clock);
    }

    @Test
    @DisplayName("요청 기록 키에는 제한 시간(10분) 이내의 만료 시간이 설정된다")
    void shouldExpireKeysAfterWindow() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        long emailTtl = redisTemplate.getExpire(
                TemporaryPasswordRateLimiter.emailKey(EMAIL), TimeUnit.SECONDS);
        long ipTtl = redisTemplate.getExpire(
                TemporaryPasswordRateLimiter.ipKey(IP), TimeUnit.SECONDS);

        assertTrue(emailTtl > 0 && emailTtl <= Duration.ofMinutes(10).toSeconds());
        assertTrue(ipTtl > 0 && ipTtl <= Duration.ofMinutes(10).toSeconds());
    }

}
