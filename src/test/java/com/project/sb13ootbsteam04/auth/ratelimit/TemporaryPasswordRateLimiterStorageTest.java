package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;
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

// Redis에 저장되는 요청 기록의 만료·정리 동작 검증
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

    @Test
    @DisplayName("10분이 지난 기록은 다음 요청에서 제거된다")
    void shouldRemoveExpiredHistoryOnNextRequest() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        advanceTime(Duration.ofMinutes(10));

        rateLimiter.checkAndRecord(EMAIL, IP);

        assertEquals(1, historySize(TemporaryPasswordRateLimiter.emailKey(EMAIL)));
        assertEquals(1, historySize(TemporaryPasswordRateLimiter.ipKey(IP)));
    }

    @Test
    @DisplayName("만료 기록을 정리해도 제한 시간 안의 기록은 유지한다")
    void shouldPreserveValidRequestHistory() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        advanceTime(Duration.ofMinutes(5));

        rateLimiter.checkAndRecord(EMAIL, IP);

        advanceTime(Duration.ofMinutes(10));

        // 첫 요청(0분)은 만료, 두 번째 요청(5분)은 유지
        rateLimiter.checkAndRecord(EMAIL, IP);

        assertEquals(2, historySize(TemporaryPasswordRateLimiter.emailKey(EMAIL)));
        assertEquals(2, historySize(TemporaryPasswordRateLimiter.ipKey(IP)));
    }

    @Test
    @DisplayName("이메일 제한으로 차단된 요청은 신규 IP 기록을 만들지 않는다")
    void shouldNotCreateIpKeyWhenEmailLimitExceeded() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        String newIp = "192.168.0.2";

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(EMAIL, newIp)
        );

        assertFalse(redisTemplate.hasKey(TemporaryPasswordRateLimiter.ipKey(newIp)));
        assertEquals(3, historySize(TemporaryPasswordRateLimiter.ipKey(IP)));
    }

    @Test
    @DisplayName("IP 제한으로 차단된 요청은 신규 이메일 기록을 만들지 않는다")
    void shouldNotCreateEmailKeyWhenIpLimitExceeded() {

        for (int i = 0; i < 10; i++) {
            rateLimiter.checkAndRecord("user" + i + "@example.com", IP);
        }

        String newEmail = "blocked@example.com";

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(newEmail, IP)
        );

        assertFalse(redisTemplate.hasKey(TemporaryPasswordRateLimiter.emailKey(newEmail)));
        assertEquals(10, historySize(TemporaryPasswordRateLimiter.ipKey(IP)));
    }

    private void advanceTime(Duration duration) {

        when(clock.instant()).thenReturn(
                INITIAL_TIME.plus(duration)
        );
    }

    private long historySize(String key) {
        Long size = redisTemplate.opsForZSet().zCard(key);
        return size == null ? 0 : size;
    }
}
