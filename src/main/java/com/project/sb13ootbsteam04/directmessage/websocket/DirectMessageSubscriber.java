package com.project.sb13ootbsteam04.directmessage.websocket;

import com.project.sb13ootbsteam04.global.redis.RedisChannelSubscriber;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 다른 서버(또는 이 서버)에서 저장된 DM을 받아 이 서버의 구독자에게 전송한다
@Component
public class DirectMessageSubscriber extends RedisChannelSubscriber {

  private final ObjectMapper objectMapper;
  private final DirectMessageRelay relay;

  public DirectMessageSubscriber(
      RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper,
      DirectMessageRelay relay
  ) {
    super(connectionFactory, DirectMessageRedisChannel.NAME, "DM");
    this.objectMapper = objectMapper;
    this.relay = relay;
  }

  @Override
  protected void handle(String body) {
    relay.deliver(objectMapper.readValue(body, DirectMessageRedisChannel.Message.class));
  }
}
