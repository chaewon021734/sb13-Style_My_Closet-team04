package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

// 알림 저장이 커밋되면 모든 인스턴스에 알린다
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationPublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final SseService sseService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void publish(NotificationCreatedEvent event) {
    var notification = event.notification();
    try {
      redisTemplate.convertAndSend(SseNotificationChannel.NAME, objectMapper.writeValueAsString(
          new SseNotificationChannel.Message(notification.receiverId(), notification)));
    } catch (DataAccessException exception) {
      // Redis 장애 시 최소한 이 인스턴스에 연결된 수신자에게는 전송
      log.warn("실시간 알림 발행 실패로 이 서버의 연결에만 전송합니다. exceptionType={}",
          exception.getClass().getSimpleName());
      sseService.send(notification);
    }
  }
}
