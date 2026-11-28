package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.global.redis.RedisChannelSubscriber;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 다른 인스턴스에서 저장된 알림을 받아 이 서버에 연결된 수신자에게 전송한다
@Component
public class SseNotificationSubscriber extends RedisChannelSubscriber {

  private final ObjectMapper objectMapper;
  private final SseService sseService;

  public SseNotificationSubscriber(
      RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper,
      SseService sseService
  ) {
    super(connectionFactory, SseNotificationChannel.NAME, "실시간 알림");
    this.objectMapper = objectMapper;
    this.sseService = sseService;
  }

  @Override
  protected void handle(String body) {
    sseService.send(objectMapper.readValue(body, SseNotificationChannel.Message.class).notification());
  }
}
