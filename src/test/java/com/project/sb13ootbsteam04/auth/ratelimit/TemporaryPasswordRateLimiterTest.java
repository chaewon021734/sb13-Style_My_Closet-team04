package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;
import com.project.sb13ootbsteam04.global.support.RedisTestContainer;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class TemporaryPasswordRateLimiterTest {

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

        // 여러 서버가 공유하는 Redis에 요청 기록을 저장
        redisTemplate = RedisTestContainer.template();
        rateLimiter = new TemporaryPasswordRateLimiter(redisTemplate, clock);
    }

    @Test
    @DisplayName("동일 이메일의 3회 요청은 허용한다")
    void shouldAllowThreeRequestsForSameEmail() {

        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(EMAIL, IP)
            );
        }
    }

    @Test
    @DisplayName("동일 이메일의 4번째 요청은 차단한다")
    void shouldRejectFourthRequestForSameEmail() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(EMAIL, IP)
        );
    }

    @Test
    @DisplayName("동일 IP의 10회 요청은 허용한다")
    void shouldAllowTenRequestsForSameIp() {

        for (int i = 0; i < 10; i++) {

            String email = "user" + i + "@example.com";

            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(email, IP)
            );
        }
    }

    @Test
    @DisplayName("동일 IP의 11번째 요청은 차단한다")
    void shouldRejectEleventhRequestForSameIp() {

        for (int i = 0; i < 10; i++) {
            rateLimiter.checkAndRecord(
                    "user" + i + "@example.com",
                    IP
            );
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        "another@example.com",
                        IP
                )
        );
    }

    @Test
    @DisplayName("이메일의 대소문자와 앞뒤 공백을 정규화한다")
    void shouldNormalizeEmail() {

        rateLimiter.checkAndRecord("USER@example.com", IP);
        rateLimiter.checkAndRecord("user@example.com", IP);
        rateLimiter.checkAndRecord(" user@example.com ", IP);

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        "User@Example.com",
                        IP
                )
        );
    }

    @Test
    @DisplayName("서로 다른 이메일과 IP는 독립적으로 제한한다")
    void shouldMaintainIndependentLimits() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        EMAIL,
                        "192.168.0.2"
                )
        );

        assertDoesNotThrow(
                () -> rateLimiter.checkAndRecord(
                        "another@example.com",
                        IP
                )
        );

        assertDoesNotThrow(
                () -> rateLimiter.checkAndRecord(
                        "different@example.com",
                        "192.168.0.2"
                )
        );
    }

    @Test
    @DisplayName("이메일 제한으로 차단된 요청은 IP 횟수를 증가시키지 않는다")
    void shouldNotIncreaseIpCountWhenEmailLimitExceeded() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        for (int i = 0; i < 10; i++) {

            assertThrows(
                    TemporaryPasswordRateLimitException.class,
                    () -> rateLimiter.checkAndRecord(EMAIL, IP)
            );
        }

        for (int i = 0; i < 7; i++) {

            String email = "other" + i + "@example.com";

            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(email, IP)
            );
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        "last@example.com",
                        IP
                )
        );
    }

    @Test
    @DisplayName("IP 제한으로 차단된 요청은 이메일 횟수를 증가시키지 않는다")
    void shouldNotIncreaseEmailCountWhenIpLimitExceeded() {

        for (int i = 0; i < 10; i++) {
            rateLimiter.checkAndRecord(
                    "user" + i + "@example.com",
                    IP
            );
        }

        for (int i = 0; i < 3; i++) {

            assertThrows(
                    TemporaryPasswordRateLimitException.class,
                    () -> rateLimiter.checkAndRecord(EMAIL, IP)
            );
        }

        String anotherIp = "192.168.0.2";

        for (int i = 0; i < 3; i++) {

            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(
                            EMAIL,
                            anotherIp
                    )
            );
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        EMAIL,
                        anotherIp
                )
        );
    }

    @Test
    @DisplayName("10분이 지나기 전에는 요청 제한을 유지한다")
    void shouldMaintainLimitBeforeWindowExpires() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        when(clock.instant()).thenReturn(
                INITIAL_TIME.plus(Duration.ofMinutes(10))
                        .minusMillis(1)
        );

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(EMAIL, IP)
        );
    }

    @Test
    @DisplayName("10분이 지나면 이메일과 IP 요청 기록이 만료된다")
    void shouldAllowRequestsAfterWindowExpires() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        for (int i = 0; i < 7; i++) {
            rateLimiter.checkAndRecord(
                    "other" + i + "@example.com",
                    IP
            );
        }

        when(clock.instant()).thenReturn(
                INITIAL_TIME.plus(Duration.ofMinutes(10))
        );

        for (int i = 0; i < 3; i++) {

            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(EMAIL, IP)
            );
        }

        for (int i = 0; i < 7; i++) {

            String email = "new" + i + "@example.com";

            assertDoesNotThrow(
                    () -> rateLimiter.checkAndRecord(email, IP)
            );
        }

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(
                        "last@example.com",
                        IP
                )
        );
    }

    @Test
    @DisplayName("서버가 여러 대여도 같은 Redis를 쓰면 요청 횟수를 합산해 제한한다")
    void shouldShareLimitAcrossInstances() {

        TemporaryPasswordRateLimiter otherInstance =
                new TemporaryPasswordRateLimiter(redisTemplate, clock);

        rateLimiter.checkAndRecord(EMAIL, IP);
        otherInstance.checkAndRecord(EMAIL, IP);
        rateLimiter.checkAndRecord(EMAIL, IP);

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> otherInstance.checkAndRecord(EMAIL, IP)
        );
    }

    @Test
    @DisplayName("Redis를 사용할 수 없으면 요청을 막지 않는다")
    void shouldAllowRequestWhenRedisIsUnavailable() {

        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("연결 실패"));

        TemporaryPasswordRateLimiter limiter =
                new TemporaryPasswordRateLimiter(unavailable, clock);

        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.checkAndRecord(EMAIL, IP));
        }
    }

    @Test
    @DisplayName("동시 요청이 발생해도 이메일 요청 제한을 우회할 수 없다")
    void shouldPreventRateLimitBypassUnderConcurrentRequests()
            throws Exception {

        int requestCount = 20;

        TemporaryPasswordRateLimiter concurrentRateLimiter =
                new TemporaryPasswordRateLimiter(
                        redisTemplate,
                        Clock.fixed(INITIAL_TIME, ZoneOffset.UTC)
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch readyLatch =
                new CountDownLatch(requestCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Boolean>> results = new ArrayList<>();

        try {

            for (int i = 0; i < requestCount; i++) {

                results.add(executor.submit(() -> {

                    readyLatch.countDown();
                    startLatch.await();

                    try {

                        concurrentRateLimiter.checkAndRecord(
                                EMAIL,
                                IP
                        );

                        return true;

                    } catch (TemporaryPasswordRateLimitException e) {

                        return false;
                    }
                }));
            }

            assertTrue(
                    readyLatch.await(10, TimeUnit.SECONDS)
            );

            startLatch.countDown();

            int allowedCount = 0;
            int rejectedCount = 0;

            for (Future<Boolean> result : results) {

                if (result.get(5, TimeUnit.SECONDS)) {
                    allowedCount++;
                } else {
                    rejectedCount++;
                }
            }

            assertEquals(3, allowedCount);
            assertEquals(17, rejectedCount);

        } finally {

            startLatch.countDown();
            executor.shutdownNow();
        }
    }
}