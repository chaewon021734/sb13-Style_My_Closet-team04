package com.project.sb13ootbsteam04.directmessage.websocket;

import com.project.sb13ootbsteam04.directmessage.dto.response.DirectMessageResponse;
import com.project.sb13ootbsteam04.directmessage.event.DirectMessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

// DM 저장이 커밋되면 Redis로 모든 서버에 알리고, 각 서버는 자기 구독자에게 전송한다
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageRelay {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  // 구독기가 이 클래스를 쓰므로 순환 참조를 피하려고 지연 조회
  private final ObjectProvider<DirectMessageSubscriber> subscriber;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(DirectMessageSentEvent event) {
    DirectMessageResponse message = event.message();
    var payload = new DirectMessageRedisChannel.Message(
        DirectMessageRedisChannel.dmKey(message.sender().userId(), message.receiver().userId()), message);
    try {
      redisTemplate.convertAndSend(DirectMessageRedisChannel.NAME, objectMapper.writeValueAsString(payload));
      // 발행은 됐지만 이 서버의 구독이 재연결 중이면 이 서버 연결에는 직접 전송
      if (!subscriber.getObject().isListening()) {
        deliver(payload);
      }
    } catch (DataAccessException exception) {
      // Redis 장애 시 최소한 이 서버에 연결된 사용자에게는 전송
      log.warn("DM 발행 실패로 이 서버의 연결에만 전송합니다. exceptionType={}",
          exception.getClass().getSimpleName());
      deliver(payload);
    }
  }

  // 이 서버에서 대화 채널을 구독 중인 사용자에게 전송
  public void deliver(DirectMessageRedisChannel.Message payload) {
    messagingTemplate.convertAndSend(DirectMessageRedisChannel.destination(payload.dmKey()), payload.message());
  }
}
