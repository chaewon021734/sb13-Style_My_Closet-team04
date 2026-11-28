package com.project.sb13ootbsteam04.global.config;

import org.springframework.boot.autoconfigure.task.TaskSchedulingProperties;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 스케줄 작업(날씨 배치, 토큰 정리, SSE 하트비트, Redis 재구독)이 서로 기다리지 않도록 스레드를 늘린다.
// 기본값은 1개라 날씨 배치가 도는 동안 하트비트가 밀린다. 더 크게 지정한 값이 있으면 그대로 둔다.
@Configuration
public class SchedulingPoolConfig {

  private static final int MIN_POOL_SIZE = 4;

  @Bean
  ThreadPoolTaskSchedulerCustomizer schedulingPoolSizeCustomizer(TaskSchedulingProperties properties) {
    return scheduler -> scheduler.setPoolSize(Math.max(properties.getPool().getSize(), MIN_POOL_SIZE));
  }
}
