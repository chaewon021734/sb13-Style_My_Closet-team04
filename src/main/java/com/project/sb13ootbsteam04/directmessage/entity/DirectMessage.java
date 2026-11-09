
package com.project.sb13ootbsteam04.directmessage.entity;

import com.project.sb13ootbsteam04.global.entity.CreatedAtEntity;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "direct_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends CreatedAtEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  public DirectMessage(User sender, User receiver, String content) {
    validateParticipants(sender, receiver);
    validateContent(content);

    this.sender = sender;
    this.receiver = receiver;
    this.content = content;
  }

  private void validateParticipants(User sender, User receiver) {
    if (sender == null || receiver == null) {
      throw new IllegalArgumentException("발신자와 수신자는 필수입니다.");
    }

    if (sender.getId().equals(receiver.getId())) {
      throw new IllegalArgumentException("자기 자신에게는 메시지를 보낼 수 없습니다.");
    }
  }

  private void validateContent(String content) {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("메시지 내용은 비어 있을 수 없습니다.");
    }
  }
}
