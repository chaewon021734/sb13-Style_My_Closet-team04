package com.project.sb13ootbsteam04.global.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

// 테스트 전체가 함께 쓰는 Redis 컨테이너
// 로컬에서 Docker를 사용할 수 없으면 Redis가 필요한 테스트만 건너뛴다(CI에서는 실패).
public final class RedisTestContainer {

    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> container;
    private static StringRedisTemplate template;

    private RedisTestContainer() {
    }

    // 컨테이너에 연결된 템플릿 (테스트 사이 데이터는 비운다)
    public static synchronized StringRedisTemplate template() {
        boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();

        // CI에서는 건너뛰지 않고 실패시켜, Redis 테스트가 실행되지 않은 채 통과하는 일을 막는다
        if (!dockerAvailable && "true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException("CI 환경에서 Docker를 사용할 수 없어 Redis 테스트를 실행할 수 없습니다.");
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker를 사용할 수 없어 Redis 테스트를 건너뜁니다.");

        if (container == null) {
            container = new GenericContainer<>("redis:8").withExposedPorts(REDIS_PORT);
            container.start();

            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(container.getHost(), container.getMappedPort(REDIS_PORT))
            );
            connectionFactory.afterPropertiesSet();
            connectionFactory.start();

            template = new StringRedisTemplate(connectionFactory);
        }

        template.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        return template;
    }

    // 스프링 컨텍스트를 테스트용 Redis에 연결할 때 사용 (spring.data.redis.host/port)
    public static String host() {
        template();
        return container.getHost();
    }

    public static int port() {
        template();
        return container.getMappedPort(REDIS_PORT);
    }
}
