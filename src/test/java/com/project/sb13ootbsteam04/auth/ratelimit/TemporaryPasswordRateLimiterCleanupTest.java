package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryPasswordRateLimiterCleanupTest {

    private static final Instant INITIAL_TIME =
            Instant.parse("2026-01-01T00:00:00Z");

    private static final String EMAIL = "user@example.com";
    private static final String IP = "192.168.0.1";

    private Clock clock;
    private TemporaryPasswordRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        clock = mock(Clock.class);

        when(clock.instant()).thenReturn(INITIAL_TIME);

        rateLimiter = new TemporaryPasswordRateLimiter(clock);
    }

    @Test
    @DisplayName("만료된 이메일 키를 삭제한다")
    void shouldRemoveExpiredEmailKeys() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        assertTrue(getEmailRequests().containsKey(EMAIL));

        advanceTime(Duration.ofMinutes(10));

        rateLimiter.checkAndRecord(
                "new@example.com",
                "192.168.0.2"
        );

        assertFalse(getEmailRequests().containsKey(EMAIL));
        assertTrue(
                getEmailRequests().containsKey("new@example.com")
        );
    }

    @Test
    @DisplayName("만료된 IP 키를 삭제한다")
    void shouldRemoveExpiredIpKeys() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        assertTrue(getIpRequests().containsKey(IP));

        advanceTime(Duration.ofMinutes(10));

        rateLimiter.checkAndRecord(
                "new@example.com",
                "192.168.0.2"
        );

        assertFalse(getIpRequests().containsKey(IP));
        assertTrue(
                getIpRequests().containsKey("192.168.0.2")
        );
    }

    @Test
    @DisplayName("메모리 정리 과정에서 유효한 요청 기록은 유지한다")
    void shouldPreserveValidRequestHistory() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        advanceTime(Duration.ofMinutes(5));

        String activeEmail = "active@example.com";
        String activeIp = "192.168.0.2";

        rateLimiter.checkAndRecord(activeEmail, activeIp);

        advanceTime(Duration.ofMinutes(10));

        rateLimiter.checkAndRecord(
                "new@example.com",
                "192.168.0.3"
        );

        assertFalse(getEmailRequests().containsKey(EMAIL));
        assertFalse(getIpRequests().containsKey(IP));

        assertTrue(
                getEmailRequests().containsKey(activeEmail)
        );

        assertTrue(
                getIpRequests().containsKey(activeIp)
        );

        assertEquals(
                1,
                getEmailRequests().get(activeEmail).size()
        );

        assertEquals(
                1,
                getIpRequests().get(activeIp).size()
        );
    }

    @Test
    @DisplayName("마지막 정리 시점부터 1분이 지나야 전체 키를 정리한다")
    void shouldRespectCleanupInterval() {

        rateLimiter.checkAndRecord(EMAIL, IP);

        // 최초 요청의 만료 직전에 전체 정리 실행
        advanceTime(
                Duration.ofMinutes(9).plusSeconds(30)
        );

        rateLimiter.checkAndRecord(
                "second@example.com",
                "192.168.0.2"
        );

        // 최초 요청은 만료됐지만 정리 주기는 지나지 않았다.
        advanceTime(Duration.ofMinutes(10));

        rateLimiter.checkAndRecord(
                "third@example.com",
                "192.168.0.3"
        );

        assertTrue(getEmailRequests().containsKey(EMAIL));
        assertTrue(getIpRequests().containsKey(IP));

        // 마지막 정리 시점부터 정확히 1분 경과
        advanceTime(
                Duration.ofMinutes(10).plusSeconds(30)
        );

        rateLimiter.checkAndRecord(
                "fourth@example.com",
                "192.168.0.4"
        );

        assertFalse(getEmailRequests().containsKey(EMAIL));
        assertFalse(getIpRequests().containsKey(IP));
    }

    @Test
    @DisplayName("이메일 제한으로 차단된 요청은 신규 IP 키를 생성하지 않는다")
    void shouldNotCreateIpKeyWhenEmailLimitExceeded() {

        for (int i = 0; i < 3; i++) {
            rateLimiter.checkAndRecord(EMAIL, IP);
        }

        String newIp = "192.168.0.2";

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(EMAIL, newIp)
        );

        assertFalse(getIpRequests().containsKey(newIp));

        assertEquals(1, getIpRequests().size());
        assertEquals(3, getIpRequests().get(IP).size());
    }

    @Test
    @DisplayName("IP 제한으로 차단된 요청은 신규 이메일 키를 생성하지 않는다")
    void shouldNotCreateEmailKeyWhenIpLimitExceeded() {

        for (int i = 0; i < 10; i++) {

            rateLimiter.checkAndRecord(
                    "user" + i + "@example.com",
                    IP
            );
        }

        String newEmail = "blocked@example.com";

        assertThrows(
                TemporaryPasswordRateLimitException.class,
                () -> rateLimiter.checkAndRecord(newEmail, IP)
        );

        assertFalse(
                getEmailRequests().containsKey(newEmail)
        );

        assertEquals(10, getEmailRequests().size());
        assertEquals(10, getIpRequests().get(IP).size());
    }

    private void advanceTime(Duration duration) {

        when(clock.instant()).thenReturn(
                INITIAL_TIME.plus(duration)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Deque<Instant>> getEmailRequests() {

        return (Map<String, Deque<Instant>>)
                ReflectionTestUtils.getField(
                        rateLimiter,
                        "emailRequests"
                );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Deque<Instant>> getIpRequests() {

        return (Map<String, Deque<Instant>>)
                ReflectionTestUtils.getField(
                        rateLimiter,
                        "ipRequests"
                );
    }
}