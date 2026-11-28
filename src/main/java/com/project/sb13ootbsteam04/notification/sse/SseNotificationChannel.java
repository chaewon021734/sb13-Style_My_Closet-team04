package com.project.sb13ootbsteam04.notification.sse;

import com.project.sb13ootbsteam04.notification.dto.NotificationDto;

import java.util.UUID;

// 인스턴스 사이에 실시간 알림을 전달하는 Redis 채널
public final class SseNotificationChannel {

  public static final String NAME = "sse:notifications";

  private SseNotificationChannel() {
  }

  // 채널로 주고받는 메시지
  public record Message(UUID receiverId, NotificationDto notification) {
  }
}
