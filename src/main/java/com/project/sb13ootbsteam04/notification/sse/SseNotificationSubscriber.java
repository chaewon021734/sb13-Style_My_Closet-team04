package com.project.sb13ootbsteam04.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

// 모든 인스턴스가 채널을 구독하고, 자기에게 연결된 수신자에게만 전송한다.
// Redis에 연결할 수 없어도 서버는 시작하고, 구독은 연결될 때까지 다시 시도한다.
@Slf4j
@Component
public class SseNotificationSubscriber implements MessageListener, SmartLifecycle, DisposableBean {

  private final ObjectMapper objectMapper;
  private final SseService sseService;
  private final RedisMessageListenerContainer container;
  private boolean failureLogged;
  private volatile boolean running;

  public SseNotificationSubscriber(
      RedisConnectionFactory connectionFactory,
      ObjectMapper objectMapper,
      SseService sseService
  ) {
    this.objectMapper = objectMapper;
    this.sseService = sseService;
    this.container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(this, new ChannelTopic(SseNotificationChannel.NAME));
    container.afterPropertiesSet();
  }

  // 서버 시작 과정에서 구독한다. 실패해도 서버 시작은 막지 않는다.
  @Override
  public void start() {
    running = true;
    subscribe();
  }

  @Override
  public void stop() {
    running = false;
    container.stop();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  // 구독이 끊겼거나 시작하지 못했으면 다시 시도
  @Scheduled(fixedDelay = 10_000)
  public synchronized void subscribe() {
    if (!running || container.isRunning()) {
      return;
    }
    try {
      container.start();
      failureLogged = false;
      log.info("실시간 알림 채널 구독을 시작했습니다.");
    } catch (RuntimeException exception) {
      container.stop();
      // 같은 실패를 반복해서 남기지 않는다
      if (!failureLogged) {
        log.warn("실시간 알림 채널을 구독하지 못해 다시 시도합니다. exceptionType={}",
            exception.getClass().getSimpleName());
        failureLogged = true;
      }
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      var payload = objectMapper.readValue(
          new String(message.getBody(), StandardCharsets.UTF_8), SseNotificationChannel.Message.class);
      sseService.send(payload.notification());
    } catch (RuntimeException exception) {
      log.warn("실시간 알림 메시지를 처리하지 못했습니다. exceptionType={}", exception.getClass().getSimpleName());
    }
  }

  @Override
  public void destroy() throws Exception {
    container.destroy();
  }
}
