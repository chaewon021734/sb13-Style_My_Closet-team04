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

  @Test
  @DisplayName("제목이 null이면 알림을 생성할 수 없다.")
  void createNotificationWithNullTitle() {
    // given
    User receiver = new User("테스트 사용자", "test@test.com", "password");
    String title = null;
    String content = "새로운 댓글이 등록되었습니다.";
    NotificationLevel level = NotificationLevel.INFO;

    // when & then
    assertThatThrownBy(() ->
        new Notification(receiver, title, content, level)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("내용이 null이면 알림을 생성할 수 없다.")
  void createNotificationWithNullContent() {
    // given
    User receiver = new User("테스트 사용자", "test@test.com", "password");
    String title = "새로운 알림";
    String content = null;
    NotificationLevel level = NotificationLevel.INFO;

    // when & then
    assertThatThrownBy(() ->
        new Notification(receiver, title, content, level)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("내용이 빈 문자열이면 알림을 생성할 수 없다.")
  void createNotificationWithEmptyContent() {
    // given
    User receiver = new User("테스트 사용자", "test@test.com", "password");
    String title = "새로운 알림";
    String content = "";
    NotificationLevel level = NotificationLevel.INFO;

    // when & then
    assertThatThrownBy(() ->
        new Notification(receiver, title, content, level)
    ).isInstanceOf(IllegalArgumentException.class);
  }
}