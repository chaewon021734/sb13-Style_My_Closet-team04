package com.project.sb13ootbsteam04.notification.entity;

import com.project.sb13ootbsteam04.global.entity.CreatedAtEntity;
import com.project.sb13ootbsteam04.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends CreatedAtEntity {

  // 사용자가 삭제되면 알림도 함께 삭제 (V16 마이그레이션의 ON DELETE CASCADE와 동일)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  @OnDelete(action = OnDeleteAction.CASCADE)
  private User receiver;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "content", nullable = false, length = 500)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, length = 20)
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
    // 수준을 지정하지 않으면 일반 알림
    this.level = level == null ? NotificationLevel.INFO : level;
  }
}
