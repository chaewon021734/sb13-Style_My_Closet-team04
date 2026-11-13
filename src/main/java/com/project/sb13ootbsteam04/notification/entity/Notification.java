package com.project.sb13ootbsteam04.notification.entity;

import com.project.sb13ootbsteam04.user.entity.User;
import lombok.Getter;

@Getter
public class Notification {

  private User receiver;
  private String title;
  private String content;
  private NotificationLevel level;

  public Notification(User receiver, String title, String content, NotificationLevel level) {
    if (receiver == null) {
      throw new IllegalArgumentException("수신자는 필수입니다.");
    }

    if (title == null) {
      throw new IllegalArgumentException("알림 제목은 필수입니다.");
    }

    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("알림 내용은 필수입니다.");
    }

    this.receiver = receiver;
    this.title = title;
    this.content = content;
    this.level = level;
  }
}