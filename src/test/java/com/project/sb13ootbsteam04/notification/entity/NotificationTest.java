package com.project.sb13ootbsteam04.notification.entity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  @DisplayName("수신자가 null이면 알림을 생성할 수 없다.")
  void createNotificationWithNullReceiver() {
    // given
    User receiver = null;
    String title = "새로운 알림";
    String content = "새로운 댓글이 등록되었습니다.";
    NotificationLevel level = NotificationLevel.INFO;

    // when & then
    assertThatThrownBy(() ->
        new Notification(receiver, title, content, level)
    ).isInstanceOf(IllegalArgumentException.class);
  }
}