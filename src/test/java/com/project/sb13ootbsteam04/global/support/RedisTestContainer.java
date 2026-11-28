package com.project.sb13ootbsteam04.global.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;

// 테스트 전체가 함께 쓰는 Redis 컨테이너
// Docker를 사용할 수 없으면 Redis가 필요한 테스트만 건너뛴다.
public final class RedisTestContainer {

    private static final int REDIS_PORT = 6379;

    private static GenericContainer<?> container;
    private static StringRedisTemplate template;

    private RedisTestContainer() {
    }

    // 컨테이너에 연결된 템플릿 (테스트 사이 데이터는 비운다)
    public static synchronized StringRedisTemplate template() {
        Assumptions.assumeTrue(
                DockerClientFactory.instance().isDockerAvailable(),
                "Docker를 사용할 수 없어 Redis 테스트를 건너뜁니다."
        );

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
}
