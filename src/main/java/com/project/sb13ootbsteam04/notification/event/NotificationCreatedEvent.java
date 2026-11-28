package com.project.sb13ootbsteam04.notification.event;

import com.project.sb13ootbsteam04.notification.dto.NotificationDto;

import java.util.Objects;

// 알림 저장 트랜잭션이 커밋된 뒤 실시간 전송에 사용한다.
public record NotificationCreatedEvent(NotificationDto notification) {

  public NotificationCreatedEvent {
    Objects.requireNonNull(notification, "알림은 필수입니다.");
  }
}
