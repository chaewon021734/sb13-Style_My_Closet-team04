package com.project.sb13ootbsteam04.global.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;

// 모든 인스턴스가 Redis 채널을 구독하고, 받은 메시지를 자기에게 연결된 사용자에게 전달한다.
// Redis에 연결할 수 없어도 서버는 시작하고, 구독은 연결될 때까지 다시 시도한다.
@Slf4j
public abstract class RedisChannelSubscriber implements MessageListener, SmartLifecycle, DisposableBean {

  private final String description;
  private final RedisMessageListenerContainer container;
  private boolean failureLogged;
  private volatile boolean running;

  protected RedisChannelSubscriber(RedisConnectionFactory connectionFactory, String channel, String description) {
    this.description = description;
    this.container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(this, new ChannelTopic(channel));
    container.afterPropertiesSet();
  }

  // 지금 채널 메시지를 받고 있는지 (Redis 재연결 중이면 false)
  public boolean isListening() {
    return container.isListening();
  }

  // 받은 메시지 본문(JSON) 처리
  protected abstract void handle(String body);

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
      log.info("{} 채널 구독을 시작했습니다.", description);
    } catch (RuntimeException exception) {
      container.stop();
      // 같은 실패를 반복해서 남기지 않는다
      if (!failureLogged) {
        log.warn("{} 채널을 구독하지 못해 다시 시도합니다. exceptionType={}",
            description, exception.getClass().getSimpleName());
        failureLogged = true;
      }
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      handle(new String(message.getBody(), StandardCharsets.UTF_8));
    } catch (RuntimeException exception) {
      log.warn("{} 메시지를 처리하지 못했습니다. exceptionType={}", description, exception.getClass().getSimpleName());
    }
  }

  @Override
  public void destroy() throws Exception {
    container.destroy();
  }
}
