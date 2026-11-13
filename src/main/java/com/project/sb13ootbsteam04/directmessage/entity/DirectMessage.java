
package com.project.sb13ootbsteam04.directmessage.entity;

import com.project.sb13ootbsteam04.global.entity.UpdatedAtEntity;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "direct_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends UpdatedAtEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  public DirectMessage(User sender, User receiver, String content) {
    // 자기 자신에게 DM 전송 방지
    if (sender == receiver || (sender.getId() != null && sender.getId().equals(receiver.getId()))) {
      throw new IllegalArgumentException("자기 자신에게는 DM을 보낼 수 없습니다.");
    }

    // null 또는 빈 메시지 방지
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("DM 메시지 내용은 비어 있을 수 없습니다.");
    }

    // 메시지 최대 길이 제한
    if (content.length() > 300) {
      throw new IllegalArgumentException("DM 메시지는 최대 300자까지 입력할 수 있습니다.");
    }

    this.sender = sender;
    this.receiver = receiver;
    this.content = content;
  }
}