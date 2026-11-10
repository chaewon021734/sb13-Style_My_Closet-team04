
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
    this.sender = sender;
    this.receiver = receiver;
    this.content = content;
  }
}