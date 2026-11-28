package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.global.support.RedisTestContainer;
import com.project.sb13ootbsteam04.notification.entity.NotificationLevel;
import com.project.sb13ootbsteam04.notification.service.NotificationService;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 서버가 Redis에 연결된 뒤 Redis가 응답하지 않게 되는 경우(실행 중 장애)
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("실행 중 Redis 장애 시 응답 시간")
class SseRedisTimeoutTest {

    // 일시 정지시켜야 하므로 다른 테스트와 함께 쓰지 않는 전용 컨테이너
    private static GenericContainer<?> redis;

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;

    @BeforeAll
    static void startRedis() {
        RedisTestContainer.template();
        redis = new GenericContainer<>("redis:8").withExposedPorts(6379);
        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        if (redis != null) {
            redis.stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> redis.getHost());
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Test
    @DisplayName("Redis가 응답하지 않아도 알림을 만드는 요청은 몇 초 안에 끝난다")
    void finishesQuicklyWhenRedisStopsResponding() {
        var user = userRepository.save(new User("사용자", UUID.randomUUID() + "@test.io", "encoded"));
        var dockerClient = redis.getDockerClient();
        dockerClient.pauseContainerCmd(redis.getContainerId()).exec();
        try {
            long started = System.nanoTime();

            notificationService.create(user.getId(), "알림", "Redis 응답 없음", NotificationLevel.INFO);

            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        } finally {
            dockerClient.unpauseContainerCmd(redis.getContainerId()).exec();
        }
    }
}
