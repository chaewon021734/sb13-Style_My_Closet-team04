
package com.project.sb13ootbsteam04.directmessage.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectMessageTest {

  //
  @Test
  @DisplayName("정상적인 발신자와 수신자로 DM 메시지를 생성할 수 있다.")
  void createDirectMessage() {
    // 발신자 수신자 생성
    User sender = new User(
        "발신자",
        "sender@test.com",
        "password123"
    );

    User receiver = new User(
        "수신자",
        "receiver@test.com",
        "password123"
    );

    String content = "안녕하세요!";

    // DM 메세지 생성
    DirectMessage directMessage = new DirectMessage(sender, receiver, content);

    // 발신자, 수신자, 내용 확인
    assertThat(directMessage.getSender()).isEqualTo(sender);
    assertThat(directMessage.getReceiver()).isEqualTo(receiver);
    assertThat(directMessage.getContent()).isEqualTo(content);
  }

  @Test
  @DisplayName("발신자와 수신자가 동일하면 DM 메시지를 생성할 수 없다.")
  void cannotSendMessageToSelf() {
    // 사용자 생성
    User user = new User(
        "사용자",
        "user@test.com",
        "password123"
    );

    // 자기 자신에게 DM 메시지를 보내면 예외 발생
    assertThatThrownBy(() -> new DirectMessage(user, user, "안녕하세요!")
    )
        .isInstanceOf(IllegalArgumentException.class);
  }

  //
  @Test
  @DisplayName("메시지 내용이 비어 있으면 DM 메시지를 생성할 수 없다.")
  void cannotCreateDirectMessageWithEmptyContent() {
    // 발신자, 수신자 생성
    User sender = new User(
        "발신자",
        "sender@test.com",
        "password123"
    );

    User receiver = new User(
        "수신자",
        "receiver@test.com",
        "password123"
    );

    // 빈 메시지로 DM을 생성하면 예외 발생
    assertThatThrownBy(() ->
        new DirectMessage(sender, receiver, "")
    )
        .isInstanceOf(IllegalArgumentException.class);
  }
}
