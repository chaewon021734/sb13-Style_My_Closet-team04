
package com.project.sb13ootbsteam04.directmessage.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.sb13ootbsteam04.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DirectMessageTest {

  @Test
  @DisplayName("정상적인 발신자와 수신자로 DM 메시지를 생성할 수 있다.")
  void createDirectMessage() {
    // given
    User sender = new User("발신자", "sender@test.com", "password123");
    User receiver = new User("수신자", "receiver@test.com", "password123");
    String content = "안녕하세요!";

    // when
    DirectMessage directMessage = new DirectMessage(sender, receiver, content);

    // then
    assertThat(directMessage.getSender()).isEqualTo(sender);
    assertThat(directMessage.getReceiver()).isEqualTo(receiver);
    assertThat(directMessage.getContent()).isEqualTo(content);
  }
}
