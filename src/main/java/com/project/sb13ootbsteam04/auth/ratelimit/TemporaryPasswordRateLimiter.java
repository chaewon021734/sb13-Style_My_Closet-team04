package com.project.sb13ootbsteam04.auth.ratelimit;

import com.project.sb13ootbsteam04.auth.exception.TemporaryPasswordRateLimitException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Component
public class TemporaryPasswordRateLimiter {

    private static final Logger log =
            LoggerFactory.getLogger(TemporaryPasswordRateLimiter.class);

    private static final Duration WINDOW =
            Duration.ofMinutes(10);

    private static final int EMAIL_LIMIT = 3;
    private static final int IP_LIMIT = 10;

    // 두 키가 같은 Redis 슬롯에 있도록 해시 태그 사용
    private static final String EMAIL_KEY_PREFIX =
            "rate-limit:{temporary-password}:email:";

    private static final String IP_KEY_PREFIX =
            "rate-limit:{temporary-password}:ip:";

    /**
     * 이메일과 IP 기록을 한 번에 검사하고 기록한다.
     * 스크립트 전체가 원자적으로 실행되어 여러 서버의 동시 요청도 제한을 우회할 수 없다.
     * KEYS: 이메일 키, IP 키
     * ARGV: 현재 시각(ms), 만료 기준 시각(ms), 이메일 제한, IP 제한, 키 만료(ms), 기록 식별자
     */
    private static final RedisScript<Long> CHECK_AND_RECORD =
            RedisScript.of("""
                    redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
                    redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
                    if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[3])
                        or redis.call('ZCARD', KEYS[2]) >= tonumber(ARGV[4]) then
                        return 0
                    end
                    redis.call('ZADD', KEYS[1], ARGV[1], ARGV[6])
                    redis.call('ZADD', KEYS[2], ARGV[1], ARGV[6])
                    redis.call('PEXPIRE', KEYS[1], ARGV[5])
                    redis.call('PEXPIRE', KEYS[2], ARGV[5])
                    return 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    /**
     * 운영 환경에서는 실제 시스템 시간을 사용한다.
     */
    @Autowired
    public TemporaryPasswordRateLimiter(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.systemUTC());
    }

    /**
     * 테스트 환경에서는 시간을 직접 제어할 수 있도록 한다.
     */
    TemporaryPasswordRateLimiter(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * 이메일과 IP별 요청 횟수를 동시에 검사한다.
     * 두 제한 중 하나라도 초과하면 요청을 거절한다.
     * 요청 기록은 Redis에 저장해 여러 서버가 같은 제한을 공유한다.
     */
    public void checkAndRecord(
            String email,
            String ipAddress
    ) {

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        String normalizedIp = ipAddress.trim();

        long now = clock.instant().toEpochMilli();
        long cutoff = now - WINDOW.toMillis();

        Long allowed;

        try {
            allowed = redisTemplate.execute(
                    CHECK_AND_RECORD,
                    List.of(emailKey(normalizedEmail), ipKey(normalizedIp)),
                    String.valueOf(now),
                    String.valueOf(cutoff),
                    String.valueOf(EMAIL_LIMIT),
                    String.valueOf(IP_LIMIT),
                    String.valueOf(WINDOW.toMillis()),
                    now + ":" + UUID.randomUUID()
            );
        } catch (DataAccessException exception) {
            // Redis 장애 시 비밀번호 찾기는 막지 않는다.
            log.warn(
                    "임시 비밀번호 요청 제한 확인에 실패해 요청을 허용합니다. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            return;
        }

        if (allowed == null || allowed == 0L) {
            throw new TemporaryPasswordRateLimitException();
        }
    }

    static String emailKey(String normalizedEmail) {
        return EMAIL_KEY_PREFIX + normalizedEmail;
    }

    static String ipKey(String normalizedIp) {
        return IP_KEY_PREFIX + normalizedIp;
    }
}
