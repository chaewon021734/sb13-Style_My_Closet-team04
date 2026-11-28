package com.project.sb13ootbsteam04.global.config;

import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Redis가 응답하지 않을 때 요청이 오래 멈추지 않도록 명령 대기 시간을 제한한다.
// 기본값(60초) 대신 2초를 쓰며, 환경 변수로 spring.data.redis.timeout을 지정하면 그 값을 따른다.
@Configuration
public class RedisTimeoutConfig {

  private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofSeconds(2);

  @Bean
  LettuceClientConfigurationBuilderCustomizer redisCommandTimeoutCustomizer(DataRedisProperties properties) {
    return builder -> {
      if (properties.getTimeout() == null) {
        builder.commandTimeout(DEFAULT_COMMAND_TIMEOUT);
      }
    };
  }
}
